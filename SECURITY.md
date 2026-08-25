# Security Policy

Outpost stores SSH private keys and opens authenticated connections to servers you
control. Security reports are taken seriously.

---

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Use GitHub's private vulnerability reporting:

> [Report a vulnerability](https://github.com/ameralkhorasani/Android_VPS_Monitor/security/advisories/new)

Include:

- What the issue is and why it matters
- Steps to reproduce, or a proof of concept
- The app version and Android version
- Any suggested fix

**Scrub credentials from your report.** If reproducing the issue involves a real
server, replace hostnames, usernames and keys with placeholders.

### What to expect

| | |
|---|---|
| Acknowledgement | Within 7 days |
| Initial assessment | Within 14 days |
| Fix or mitigation plan | Communicated once assessed |

This is a volunteer-maintained project without a paid security team, so timelines are
best-effort rather than contractual. You will be credited in the advisory and the
changelog unless you prefer otherwise. There is no bug bounty.

Please allow a reasonable window for a fix before public disclosure.

---

## Supported versions

Only the latest release receives security fixes.

| Version | Supported |
|---|---|
| Latest release | ✅ |
| Anything older | ❌ |

---

## Scope

### In scope

- Extraction of stored private keys or passphrases from the device
- Weaknesses in how keys are encrypted at rest
- Man-in-the-middle or host-key verification weaknesses
- Port forwards binding more widely than intended, exposing a tunnelled service
- Command injection through server output, hostnames, or user-supplied fields
- Leakage of key material into logs, crash reports, backups, or the clipboard

### Out of scope

- Attacks requiring a **rooted or already-compromised device**. Outpost cannot defend
  against an attacker who already controls the OS.
- Attacks requiring **physical access to an unlocked device**.
- Vulnerabilities in the SSH server you connect to.
- Vulnerabilities in third-party dependencies that are already public and have an
  upstream fix — open a normal issue for a dependency bump instead.
- Missing hardening that has no demonstrated impact (report with an exploit path).

---

## Security design

Documented in full in the [Security section of the README](README.md#security).
In summary:

- **Private keys are encrypted at rest** with AES-256-GCM. The key is generated in the
  **Android Keystore** and is non-exportable; on most modern hardware it lives in a
  secure element and never enters app memory.
- **Key-based authentication only.** Password auth is not implemented.
- **No network traffic except SSH** to servers you configure. No telemetry, no crash
  reporting service, no accounts.
- **`allowBackup="false"`** so key material is never swept into a cloud backup.
- **Cleartext HTTP restricted to loopback** by network security config, so tunnelled
  services are reachable at `http://localhost:<port>` and nothing else is.
- **Tunnelled services stay private** — a port forward binds to the phone's loopback
  interface and carries traffic only inside the SSH channel.

### Known limitations

These are documented rather than hidden:

- **No third-party security audit has been performed.** Read the code before trusting
  it with production infrastructure.
- **Host key verification is trust-on-first-use.** On an untrusted network, verify the
  fingerprint out of band before accepting it.
- A rooted or compromised device undermines every guarantee above.

---

## For contributors

The repository contains **no credentials of any kind** — no IPs, hostnames,
usernames, passwords, private keys, or server-identifying data. Every example uses a
documented placeholder.

`local.properties`, `*.apk`, `*.jks`, `*.keystore` and build output are gitignored.
Keep it that way. See [CONTRIBUTING.md](CONTRIBUTING.md).
