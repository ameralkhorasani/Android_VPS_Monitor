# Contributing to Outpost

Thanks for considering a contribution. Bug reports, fixes, features, documentation
and translations are all welcome.

---

## The one rule that matters most

**Never commit anything that identifies a real server.**

No hostnames, IP addresses, usernames, passwords, private keys, or screenshots
containing live output. This project is deliberately free of credentials and must
stay that way.

Use these placeholders instead:

| Instead of | Use |
|---|---|
| a real hostname | `your.vps.example.com` |
| a real IP | `203.0.113.10` (RFC 5737 documentation range) |
| a real username | `your-username` |
| a real key path | `path/to/private_key` |

Redact screenshots before attaching them. If you accidentally commit something
sensitive, say so immediately in the pull request — rotating the credential matters
far more than the embarrassment.

---

## Getting set up

```bash
git clone https://github.com/ameralkhorasani/Android_VPS_Monitor.git
cd Android_VPS_Monitor
```

Create `local.properties` pointing at your Android SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

Then build:

```bash
./gradlew assembleDebug
```

You need **JDK 17** and **Android SDK Platform 35**. See [docs/BUILDING.md](docs/BUILDING.md)
for troubleshooting, including the common `JAVA_HOME` problem.

---

## Workflow

1. **Open an issue first** for anything larger than a small fix, so effort is not
   duplicated and the approach can be agreed before you write code.
2. **Fork, then branch** from `main`:
   ```bash
   git checkout -b feature/short-description
   ```
   Use `feature/`, `fix/`, `docs/` or `refactor/` prefixes.
3. **Make your change.**
4. **Run the checks:**
   ```bash
   ./gradlew check
   ```
5. **Commit** with a clear message (see below).
6. **Open a pull request** describing what changed and how you verified it.

---

## Code style

- **Kotlin official style**, four-space indent, 100-column soft limit. `.editorconfig`
  in the repository root encodes this; most editors pick it up automatically.
- **Comments explain *why*, not *what*.** The existing codebase does this well — look
  at `HealthScore.kt` or `TunnelService.kt` for the tone. A comment that restates the
  code is noise; a comment that records a decision is valuable.
- **No new hardcoded UI strings.** Add them to `res/values/strings.xml` so the app
  stays translatable.
- **Match the surrounding code.** If a file uses a pattern you dislike, that is a
  separate pull request, not a drive-by change.

### Architectural expectations

| Layer | Rule |
|---|---|
| `ui/screens/` | Composables are stateless. They receive a `UiState` and emit events upward. No I/O, no `SSHClient`. |
| `ui/*ViewModel` | Owns `StateFlow<UiState>`. Calls repositories. Never touches Android views. |
| `ssh/`, `data/` | All I/O lives here. Fallible operations return `Result<T>`, never throw across the boundary. |
| `domain/` | Pure Kotlin. **No Android imports at all** — this is what keeps it unit-testable. |

---

## Testing

Add tests for logic changes. In particular:

- **Anything that parses server output must have a fixture-based unit test.** Server
  output varies wildly between distributions, and a parser regression silently shows
  users wrong numbers. Put sample `/proc` output in the test, not on a live server.
- `domain/` and parser code belong in `app/src/test/` (plain JVM, fast).
- Room migrations and Keystore behaviour belong in `app/src/androidTest/`.

```bash
./gradlew testDebugUnitTest          # fast, no device needed
./gradlew connectedDebugAndroidTest  # needs a device or emulator
```

### Testing against a real server

Use a throwaway VM or a local container — never production:

```bash
docker run -d --name outpost-test -p 2222:22 \
  -e USER_NAME=your-username \
  -e PUBLIC_KEY="$(cat ~/.ssh/outpost_phone.pub)" \
  linuxserver/openssh-server
```

---

## Commit messages

```
Short imperative summary under 72 characters

Explain why the change is needed and what approach you took. Wrap at 72
columns. Reference issues with "Fixes #123".
```

Good: `Fix CPU percentage on kernels without steal time`
Less good: `bugfix`, `updates`, `wip`

---

## Pull requests

A good pull request:

- Does **one thing**. Split unrelated changes.
- Explains **how you verified it** — which device, which server OS, what you observed.
- Includes screenshots for UI changes (redacted).
- Passes `./gradlew check`.

Reviews are about the code, never the person. Expect questions; they are how the
project stays maintainable.

---

## Good first issues

Look for the [`good first issue`](https://github.com/ameralkhorasani/Android_VPS_Monitor/labels/good%20first%20issue)
label. Approachable areas:

- Additional keys in the terminal key row
- More `/proc` metrics (per-core CPU, temperature, per-disk I/O)
- More log presets beyond `journalctl`
- Widening `StatsParser` test coverage across distributions
- Translations

---

## Reporting bugs

Open an issue using the bug template. Include your Android version, device, the
server OS, and what you expected versus what happened. **Scrub hostnames and
usernames from any logs you paste.**

## Reporting vulnerabilities

Do **not** open a public issue. See [SECURITY.md](SECURITY.md).

---

## Code of conduct

By participating you agree to abide by the [Code of Conduct](CODE_OF_CONDUCT.md).

## Licence

Contributions are licensed under the [MIT License](LICENSE), the same as the project.
