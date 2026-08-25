# Screenshots

These are the captures the top-level [README](../../README.md) renders.

| Filename | Screen |
|---|---|
| `overview.png` | Server list with health score and CPU / RAM / disk bars |
| `monitor.png` | Live CPU, RAM and swap graphs |
| `processes.png` | Top processes by CPU |
| `docker.png` | Docker containers, with per-container CPU and memory |
| `overview-empty.png` | First run, before any server is added |

Still missing, if you want to fill them in: the SSH terminal, the port-forwarding
screen, and log tailing. Add the file here and reference it from the README table.

## Before you commit a screenshot

**Redact every server detail.** Hostnames, IP addresses, usernames, container names
that identify infrastructure, and anything visible in terminal scrollback.

A screenshot is the easiest way to leak a hostname into a public repository, and it
cannot be un-published once pushed. If in doubt, capture against a throwaway VM named
something like `your-vps` rather than editing a real one.
