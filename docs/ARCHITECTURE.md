# Architecture

How Outpost is put together, and why it is put together that way.

---

## The shape of the app

Outpost is a **single-module Android app** using **MVVM with a repository layer**,
built on Kotlin coroutines and Flow throughout.

```
┌──────────────────────────────────────────────────────────────┐
│  ui/                    Jetpack Compose + Material 3         │
│                         Stateless composables that render a  │
│                         UiState and emit events upward.      │
└───────────────────────────┬──────────────────────────────────┘
                            │  StateFlow<UiState>
┌───────────────────────────┴──────────────────────────────────┐
│  ui/screens/*ViewModel   Hilt-injected, one per screen.      │
│                         Owns UiState, runs work in           │
│                         viewModelScope. Never touches an     │
│                         SSHClient or an Android View.        │
└───────────────────────────┬──────────────────────────────────┘
                            │  suspend fun / Flow, returning Result<T>
┌───────────────────────────┴──────────────────────────────────┐
│  ssh/ + data/            All I/O lives here.                 │
└──────────┬──────────────────────────────┬────────────────────┘
           │                              │
┌──────────┴───────────┐      ┌───────────┴─────────────────────┐
│  data/               │      │  ssh/                           │
│  Room, Keystore,     │      │  SSHJ: exec channels, PTY       │
│  EncryptedSharedPrefs│      │  sessions, local port forwards  │
└──────────────────────┘      └─────────────────────────────────┘

                    ┌─────────────────────┐
                    │  domain/            │  Pure Kotlin.
                    │  No Android imports │  Depended on by
                    └─────────────────────┘  data/ and ui/.
```

### Package responsibilities

| Package | Contains | Depends on |
|---|---|---|
| `core/` | Cross-cutting utilities: crash reporting, `Result` helpers | Android framework only |
| `domain/` | Health scoring, alert thresholds | **Nothing.** Pure Kotlin |
| `data/db/` | Room database, DAOs, migrations | `data/model`, `domain` |
| `data/model/` | Room entities and value types | `domain` |
| `data/preferences/` | Settings persistence | Android framework |
| `data/security/` | Keystore wrapper, SSH key parsing and validation | Android framework |
| `ssh/` | SSH client, `/proc` metric sampling and parsing | `data`, `core` |
| `ssh/docker/` | Docker container listing and control | `ssh` |
| `ssh/tunnel/` | Local port forwarding, foreground service | `ssh`, `data/db` |
| `di/` | Hilt modules | everything |
| `ui/` | Compose screens, ViewModels, navigation, theme | `domain`, repositories |

The dependency rule: **`domain/` depends on nothing, and nothing in `data/`,
`domain/` or `ssh/` depends on `ui/`.** That is what keeps the logic testable and
what a future `:core` / `:feature:*` module split would follow.

---

## Key decisions

### Agentless monitoring

Outpost installs nothing on your servers. `SshRepository.sampleStats()` runs one
batched shell command over an SSH exec channel, and `StatsParser` turns the raw text
into a `ServerStats`.

The alternative — shipping a monitoring agent — would mean asking users to install a
binary on production infrastructure and keep it updated. Reading `/proc` with stock
tools works on essentially every Linux distribution and requires no trust beyond the
SSH access the user already has.

**Consequence:** CPU and network are *rate* quantities, computed from the delta
between two samples. `StatsParser` holds `previousCpuSnapshot`, `previousNetSnapshot`
and `previousTimestamp` as mutable state, which means:

- The **first sample after connecting reads zero** by design, not by bug.
- A `StatsParser` instance is **stateful and not thread-safe**. One per connection.

CPU is computed from `/proc/stat` including `iowait` and `steal`, so a VPS with a
noisy neighbour reports honestly rather than looking idle.

### Private keys never touch disk unencrypted

`data/security/SecureKeyManager` generates an **AES-256-GCM key inside the Android
Keystore** and uses it to encrypt SSH private keys and passphrases before Room ever
sees them. The Keystore key is non-exportable: it cannot be read out of the device
even with root, and on most modern hardware it lives in a secure element.

`ServerEntity.encryptedPrivateKey` therefore holds Base64 ciphertext, never a key.

**Password authentication is deliberately not implemented.** Supporting it would mean
storing a reusable secret that is far more dangerous if extracted than a key that can
be revoked from `authorized_keys`.

### Tunnels outlive the UI

A port forward whose purpose is *"browse `http://localhost:8090` in Chrome"* is
useless if it dies the moment you leave the app. So:

- `ssh/tunnel/TunnelService` is a **foreground service** with a persistent
  notification. Android will not kill it while a tunnel is open.
- `ssh/tunnel/TunnelManager` owns tunnel lifecycle, exposes
  `StateFlow<List<ActiveTunnel>>`, and **reuses one SSH connection per server**
  rather than opening a connection per forward.
- `pruneDead()` reconciles the tracked list against reality, because a tunnel can die
  underneath the manager when the network changes.

### Migrations are written, not generated away

`data/db/AppDatabase` is at **version 4** with explicit `Migration` objects for
1→2, 2→3 and 3→4. `fallbackToDestructiveMigration()` is deliberately *not* used.

A destructive fallback would silently delete every stored server — including
encrypted SSH keys the user may not have backed up — on an app update. That is not an
acceptable failure mode for a credentials-holding app, so migrations are written by
hand and tested in `androidTest`.

### `Result<T>` rather than exceptions

A dropped connection, a refused port, a missing `docker` binary — these are **expected
outcomes**, not exceptional ones. `core/result/SuspendCatching` wraps I/O so that
failures arrive as `Result.failure` with a message suitable for showing a user,
instead of propagating as crashes.

ViewModels therefore render errors as state rather than defending with try/catch.

### Health score is relative, not absolute

`domain/HealthScore` scores a server against **its own configured thresholds** rather
than fixed constants. A backup box that is expected to sit at 80% disk should not be
reported as sick.

Nothing is deducted until a metric reaches 75% of its threshold; the penalty then
grows linearly and saturates once the threshold is crossed. Weights: CPU 25, RAM 25,
disk 30, leaving headroom for future signals.

### Terminal rendering

A correct VT100/xterm emulator is a large, subtle piece of software. Rather than
write one, Outpost embeds **xterm.js** in a `WebView` and bridges it to the SSH PTY
stream: bytes from the channel go to the JS side, keystrokes come back.

This is why `htop`, `vim` and `tmux` behave correctly — the terminal is the same
implementation used by VS Code. The cost is a `WebView`, which is why the network
security config restricts cleartext to loopback.

---

## Data flow: a live metrics screen

```
MonitorScreen (Compose)
   │  collectAsStateWithLifecycle()
   ▼
MonitorViewModel
   │  viewModelScope.launch { ... }
   ▼
SshRepository.getRealtimeStats(client): Flow<ServerStats>
   │  every N seconds
   ▼
SSHJ exec channel ──► `cat /proc/stat; cat /proc/meminfo; df; ...`
   │  raw text
   ▼
StatsParser  ──► delta against previous sample ──► ServerStats
   │
   ├─► emitted to the UI as UiState
   └─► cached onto ServerEntity (lastCpuPercent, …) so the Overview
       can show real numbers without holding a connection open
```

That cached-metrics detail is why the Overview screen is fast: it reads the last
observed values from Room instead of connecting to every server on launch.

---

## Threading

- All I/O is `suspend` and runs on `Dispatchers.IO`.
- ViewModels launch in `viewModelScope`; work is cancelled with the screen.
- `TunnelManager` holds its own scope, because a tunnel must outlive the screen that
  started it.
- Compose state is only ever touched on the main thread; repositories never touch it
  at all.

---

## Testing strategy

| Layer | Where | Why there |
|---|---|---|
| `domain/`, `StatsParser` | `app/src/test/` | No Android dependencies, so they run on the JVM in milliseconds against fixture text |
| Room migrations | `app/src/androidTest/` | Needs a real SQLite instance |
| `SecureKeyManager` | `app/src/androidTest/` | Needs a real Android Keystore |

Parsing logic is kept free of Android imports **specifically** so that server-output
handling can be tested exhaustively without a server. A parser regression shows users
silently wrong numbers, which is worse than a crash.

---

## Why one module?

At roughly 10,000 lines, a `:core` / `:feature:*` split would cost contributors more
in build-file complexity and navigation overhead than it returns in incremental build
speed.

The package boundaries above are already the seams such a split would follow. When
the project outgrows one module, `domain/` becomes `:core:domain`, `ssh/` becomes
`:core:ssh`, and each `ui/screens/*` package becomes a `:feature:*` — no code
reorganisation required, only build files.
