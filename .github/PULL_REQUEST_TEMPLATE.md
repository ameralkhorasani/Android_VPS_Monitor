## What does this change?

<!-- A clear description of the change and why it is needed. -->

Fixes #

## How did you verify it?

<!--
Be specific. "Tested it" is not verification.
For example: "Built debug, installed on a Pixel 6 running Android 14, connected to a
Debian 12 VM, watched the CPU graph for two minutes and compared against htop."
-->

- Device / Android version:
- Server OS (if relevant):
- What I observed:

## Type of change

- [ ] Bug fix (non-breaking change that fixes an issue)
- [ ] New feature (non-breaking change that adds functionality)
- [ ] Breaking change (existing behaviour changes)
- [ ] Documentation
- [ ] Refactor / internal cleanup

## Checklist

- [ ] `./gradlew check` passes
- [ ] I added or updated tests for logic changes
- [ ] Anything parsing server output has a fixture-based unit test
- [ ] New user-facing strings are in `res/values/strings.xml`, not hardcoded
- [ ] Comments explain *why*, not *what*
- [ ] I updated documentation if behaviour changed
- [ ] I added a `CHANGELOG.md` entry under `[Unreleased]`

## Security checklist

**Required — this project guarantees it contains no credentials.**

- [ ] No hostnames, IP addresses, usernames, passwords or private keys are included
- [ ] Any screenshots are redacted
- [ ] No new secret-bearing files are committed
- [ ] Placeholders (`your.vps.example.com`, `your-username`) are used in all examples

## Screenshots

<!-- For UI changes. Redact any server details before uploading. -->
