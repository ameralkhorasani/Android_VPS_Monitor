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

### Changed
- Reorganised the source tree into `core/`, `data/`, `domain/`, `ssh/` and `ui/`
  layers. `domain/` is pure Kotlin with no Android dependencies, which makes the
  health-scoring logic unit-testable on the JVM.
- Split the former `data/ssh` package into `ssh/`, `ssh/docker/`, `ssh/editor/` and
  `ssh/tunnel/`, which are independent subsystems that merely share a transport.
- Pinned the Gradle wrapper to a stable 8.11.1 release instead of a pre-release
  milestone build.
- Raised the Compose BOM to `2024.09.00`. Material 3 1.3.0 is required by
  `DropdownMenu(containerColor = …)`, which the Overview screen already used.

### Fixed
- The debug build did not compile: the Overview screen used a Material 3 API newer
  than the pinned Compose BOM provided.

### Removed
- Build output, a checked-in debug APK, and machine-local configuration that had been
  committed to version control.

### Known gaps
- Screen text is still inlined in the composables rather than in `strings.xml`, so the
  app is not yet translatable. `strings.xml` exists and is the destination.
- `CrashReporter` contains crashes from threads whose name starts with
  `outpost-code-tunnel`, but no thread is given that name; the forwarding threads are
  called `outpost-forward-<port>`. The mismatch predates the rename and was carried
  across unchanged rather than silently altering runtime behaviour.

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
- Remote editor support: detect, install and launch `code-server` on the server, then
  reach it through an automatically created SSH tunnel.
- Per-server alert thresholds for CPU, RAM, disk and TLS expiry.
- Light, dark and system themes.
- Private keys encrypted at rest with an AES-256-GCM key held in the Android Keystore.
- Non-destructive Room migrations so upgrades never drop stored servers or keys.
- Safe-mode screen that surfaces a startup crash instead of failing silently.

[Unreleased]: https://github.com/ameralkhorasani/Android_VPS_Terminal/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/ameralkhorasani/Android_VPS_Terminal/releases/tag/v1.0.0
