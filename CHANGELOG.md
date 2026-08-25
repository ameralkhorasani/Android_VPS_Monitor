# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- MIT licence, contribution guide, code of conduct, and security policy.
- Continuous integration: build, unit tests and lint on every pull request, plus a
  job that fails the build if a credential or routable IP is ever committed.
- Unit tests for `StatsParser` (24 cases against `/proc` fixtures) and `HealthScore`.
- Room schema export, so migrations become testable with `MigrationTestHelper` from
  the next schema version onward.
- `app/proguard-rules.pro`, which the release build type referenced but which did not
  exist. Includes the SSHJ and BouncyCastle keep rules that reflective algorithm
  lookup requires.
- Adaptive launcher icon with a themed-icon layer for Android 13+.
- `res/values/strings.xml` and a night-mode theme, so the launch window no longer
  flashes a light background on a dark device.
- `docs/ARCHITECTURE.md` and `docs/BUILDING.md`.
- A **Docker** tab on the server screen and in the bottom navigation bar, so containers
  are reachable from the same place as the live monitor, processes and uptime.
- The dashboard now says how old its figures are ("Updated 2m ago") and re-probes when
  it comes back to the foreground with stale numbers, subject to the existing
  probe-on-startup setting.
- The log viewer asks the server which logs it actually has: the systemd journal and
  every readable file under `/var/log`, each probed as the SSH user and then through
  passwordless `sudo`. A custom path can be opened for anything it did not find.

### Changed
- The bottom navigation bar carries Overview, Monitor, Logs and Docker. The per-server
  tabs are disabled until a server has been opened rather than navigating to an empty
  screen, and the terminal is opened from a server card - a tab that reopens a shell on
  every tap is worse than a deliberate button.
- Reorganised the source tree into `core/`, `data/`, `domain/`, `ssh/` and `ui/`
  layers. `domain/` is pure Kotlin with no Android dependencies, which makes the
  health-scoring logic unit-testable on the JVM.
- Split the former `data/ssh` package into `ssh/`, `ssh/docker/` and `ssh/tunnel/`,
  which are independent subsystems that merely share a transport.
- Pinned the Gradle wrapper to a stable 8.11.1 release instead of a pre-release
  milestone build.
- Raised the Compose BOM to `2024.09.00`. Material 3 1.3.0 is required by
  `DropdownMenu(containerColor = …)`, which the Overview screen already used.

### Fixed
- The debug build did not compile: the Overview screen used a Material 3 API newer
  than the pinned Compose BOM provided.
- The Overview tab in the bottom bar did not reliably return to the dashboard. It now
  pops back to it instead of navigating, which with `saveState`/`restoreState` in play
  could rebuild the dashboard on top of the stack rather than returning to it.
- Selecting the Logs tab on the server screen left the tab indicator on a tab with no
  body, so returning from the log viewer showed a blank page.
- The log viewer offered a fixed list of file paths - `/var/log/syslog` and friends -
  that most current distributions never create, and read them with a bare `sudo` that
  hangs when it decides to prompt for a password on a channel with no terminal. Every
  option therefore came back empty on a typical VPS.
- The Docker screen showed "No containers on this server" during the second or two
  `docker ps` and `docker stats` take to answer, then filled itself in. It now says it
  is still reading.
- `CrashReporter` matched a thread prefix (`outpost-code-tunnel`) that no thread ever
  had; the forwarding threads are named `outpost-forward-<port>`.

### Removed
- The remote editor (`code-server`) integration: the Code screen, its repository and
  setup script, the per-server and default port settings, and the bottom-bar entry.
  Port forwarding covers reaching a service on the VPS, and did so more reliably.
  The two `servers` columns it added stay in place - dropping a column in SQLite means
  rebuilding a table that holds encrypted private keys, which is not a trade worth
  making for two unread fields.
- Build output, a checked-in debug APK, and machine-local configuration that had been
  committed to version control.

### Known gaps
- Screen text is still inlined in the composables rather than in `strings.xml`, so the
  app is not yet translatable. `strings.xml` exists and is the destination.

## [1.0.0] — Unreleased

Initial release.

### Added
- SSH terminal with a full PTY rendered by xterm.js, including colour, cursor
  addressing, window resize, and a key row for `Esc`, `Tab`, `Ctrl`, `Alt` and arrows.
- Agentless live monitoring of CPU, memory, swap, disk, load average, network
  throughput and uptime, read from `/proc` over SSH.
- A 0–100 health score per server, weighted against that server's own alert
  thresholds.
- SSH local port forwarding backed by a foreground service, so tunnels survive
  leaving the app. Saved forwards can auto-start.
- Docker container listing, start/stop/restart, and live log streaming.
- Log tailing for `journalctl` and arbitrary files.
- Per-server alert thresholds for CPU, RAM, disk and TLS expiry.
- Light, dark and system themes.
- Private keys encrypted at rest with an AES-256-GCM key held in the Android Keystore.
- Non-destructive Room migrations so upgrades never drop stored servers or keys.
- Safe-mode screen that surfaces a startup crash instead of failing silently.

[Unreleased]: https://github.com/ameralkhorasani/Android_VPS_Monitor/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/ameralkhorasani/Android_VPS_Monitor/releases/tag/v1.0.0
