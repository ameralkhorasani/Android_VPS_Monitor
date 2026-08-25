# Building Outpost

Everything you need to get from a fresh clone to an APK on a device.

---

## Prerequisites

| | Version | Notes |
|---|---|---|
| **JDK** | 17 | Newer JDKs are **not** supported by AGP 8.5 |
| **Android SDK** | Platform 35 | Install via Android Studio's SDK Manager or `sdkmanager` |
| **Build Tools** | 35.x | |
| **Gradle** | 8.11.1 | Supplied by the wrapper — do not install it separately |
| **Android Studio** | Ladybug (2024.2.1)+ | Optional; the command line is enough |

---

## Quick start

```bash
git clone https://github.com/ameralkhorasani/Android_VPS_Monitor.git
cd Android_VPS_Monitor
```

Create `local.properties` in the repository root pointing at your SDK:

```properties
# macOS
sdk.dir=/Users/you/Library/Android/sdk

# Linux
sdk.dir=/home/you/Android/Sdk

# Windows — note the escaped colon and backslashes
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

> `local.properties` is gitignored. It describes *your* machine and must never be
> committed.

Then:

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

---

## Common tasks

| Command | What it does |
|---|---|
| `./gradlew assembleDebug` | Build the debug APK |
| `./gradlew installDebug` | Build and install on a connected device |
| `./gradlew assembleRelease` | Build an unsigned release APK |
| `./gradlew testDebugUnitTest` | JVM unit tests — fast, no device |
| `./gradlew connectedDebugAndroidTest` | Instrumented tests — needs a device |
| `./gradlew lintDebug` | Android lint |
| `./gradlew check` | Unit tests + lint |
| `./gradlew clean` | Delete build output |
| `./gradlew :app:dependencies` | Print the dependency tree |

On Windows use `gradlew.bat` instead of `./gradlew`.

---

## Troubleshooting

### `ERROR: JAVA_HOME is set to an invalid directory`

Gradle needs `JAVA_HOME` to point at a **JDK installation root**, not at its `bin`
directory and not at a `java.exe` shim.

```bash
# Wrong — this is the bin directory
JAVA_HOME=C:\Java\bin

# Right — the installation root
JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
```

Find your JDK root:

```bash
# macOS / Linux
/usr/libexec/java_home -v 17     # macOS
readlink -f "$(which java)" | sed 's|/bin/java||'

# Windows (Git Bash)
where java
```

Set it for a single command without changing your system:

```bash
JAVA_HOME="/path/to/jdk-17" ./gradlew assembleDebug
```

Android Studio ships its own JDK at `<studio>/jbr`, which also works.

### `Connection reset` while downloading Gradle

The wrapper download was interrupted, leaving a corrupt partial distribution. Delete
it and retry:

```bash
rm -rf ~/.gradle/wrapper/dists/gradle-8.11.1-all
./gradlew --version
```

### `No parameter with name 'containerColor' found`

A Compose Material 3 API is being used that the pinned Compose BOM does not provide.
Check `composeBom` in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) —
`DropdownMenu(containerColor = …)` requires Material 3 1.3.0, which arrived in BOM
`2024.09.00`.

### `SDK location not found`

`local.properties` is missing or `sdk.dir` is wrong. See [Quick start](#quick-start).
Alternatively set the `ANDROID_HOME` environment variable.

### `Unable to strip the following libraries`

A harmless warning from the NDK stripping step for a prebuilt `.so`. The APK is fine.

### Build succeeds but the app crashes at launch

Outpost catches unrecoverable startup crashes and shows a **safe mode** screen with
the stack trace instead of dying silently. Read it — it usually names the cause
directly. `adb logcat` has the full detail.

---

## Building a signed release

**Never commit a keystore or its passwords.** `.gitignore` blocks `*.jks` and
`*.keystore`, but the real protection is not creating them inside the repository.

Generate a key once:

```bash
keytool -genkey -v -keystore ~/outpost-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias outpost
```

Build with it, passing the secrets in rather than writing them to a file:

```bash
./gradlew assembleRelease \
  -Pandroid.injected.signing.store.file="$HOME/outpost-release.jks" \
  -Pandroid.injected.signing.store.password="$KEYSTORE_PASSWORD" \
  -Pandroid.injected.signing.key.alias=outpost \
  -Pandroid.injected.signing.key.password="$KEY_PASSWORD"
```

In CI, the keystore is stored base64-encoded as the `KEYSTORE_BASE64` repository
secret and decoded at build time — see
[`.github/workflows/release.yml`](../.github/workflows/release.yml).

---

## Project layout

See the [Project structure](../README.md#project-structure) section of the README and
[ARCHITECTURE.md](ARCHITECTURE.md) for how the layers fit together.
