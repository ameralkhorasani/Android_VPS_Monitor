# Screenshots

The README references these filenames. Drop real captures here and they render
automatically:

| Filename | Screen |
|---|---|
| `overview.png` | Server list with health scores |
| `monitor.png` | Live CPU / RAM / disk / network |
| `terminal.png` | SSH terminal session |
| `ports.png` | Port forwards |
| `docker.png` | Docker containers |
| `logs.png` | Log tailing |

## Before you commit a screenshot

**Redact every server detail.** Hostnames, IP addresses, usernames, container names
that identify infrastructure, and anything visible in terminal scrollback.

A screenshot is the easiest way to leak a hostname into a public repository, and it
cannot be un-published once pushed. If in doubt, capture against a throwaway VM named
something like `your-vps` rather than editing a real one.
