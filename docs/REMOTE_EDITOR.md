# VS Code on your VPS, from your phone

Outpost can run the full VS Code UI against your VPS, inside the app, using the same SSH
key you already use for the terminal. This document explains how it works and how to set
it up.

## How it works

```
Android app                                 VPS
┌───────────────────────┐                   ┌──────────────────────────┐
│ CodeScreen (WebView)  │                   │                          │
│   http://127.0.0.1:N  │                   │  code-server             │
│          │            │                   │  127.0.0.1:8080          │
│          ▼            │                   │          ▲               │
│ CodeServerTunnel      │══ SSH (key auth) ═╪══════════╝               │
│ loopback port N       │   encrypted       │                          │
└───────────────────────┘                   └──────────────────────────┘
```

1. The app opens an SSH connection with your private key (the same one stored for the
   server, encrypted with the Android Keystore).
2. It opens an SSH **local port forward** from an ephemeral loopback port on the phone to
   `127.0.0.1:<code-server port>` on the VPS.
3. A WebView loads `http://127.0.0.1:<local port>/`, which is VS Code.

The important consequence: **code-server never listens on a public interface.** There is
no port to scan, no TLS certificate to obtain, and no second password exposed to the
internet. The only way in is the SSH key.

The `http://` in the WebView is not a downgrade — that traffic never leaves the phone.
It is handed directly to the encrypted SSH channel. `network_security_config.xml`
permits cleartext for loopback only; everything else on the network still requires TLS.

## Setting it up

### Option A — from the app (recommended)

1. Add your server in the app (host, port, username, private key) as usual.
2. Leave **VS Code (code-server) Port** at `8080` unless you already run something there.
3. Open the server and tap the **Code** tab (or the `</>` icon on the server card).
4. The app probes the VPS. If code-server is missing or not running, you get an
   **Install & start code-server** button. Tap it and watch the log stream in.
5. When it finishes, the editor loads. The app stores the generated code-server password
   encrypted on the phone and signs you in automatically from then on.

Installation takes a few minutes on a fresh server.

### Option B — manually on the VPS

The same script the app uses lives at
[`app/src/main/assets/scripts/setup_code_server.sh`](../app/src/main/assets/scripts/setup_code_server.sh).
Copy it to the VPS and run it:

```bash
scp app/src/main/assets/scripts/setup_code_server.sh you@your-vps:/tmp/
ssh you@your-vps 'bash /tmp/setup_code_server.sh 8080'
```

It prints `OUTPOST_RESULT_PASSWORD=...` at the end — that is the code-server login password.
The app will pick it up automatically the next time it connects, or you can type it into
the login page once.

What the script does:

- Installs code-server via the official installer (falls back to a standalone install
  under `$HOME/.local` when there is no root or passwordless sudo).
- Writes `~/.config/code-server/config.yaml` with `bind-addr: 127.0.0.1:PORT`,
  `auth: password`, and a random password. Mode `600`.
- Enables the `code-server@<user>` systemd service so it survives reboots. Without
  systemd it falls back to a `nohup` background process (which does **not** survive a
  reboot; logs go to `~/.code-server.log`).
- Waits up to 30 s for the port to accept connections and reports failure with the
  relevant log lines.

The script is idempotent: re-running it reuses the existing password rather than locking
you out of an existing installation.

## Using the editor

- **Desktop / mobile layout** — the overflow menu toggles the user agent. code-server
  serves a reduced layout to mobile user agents; the app defaults to the desktop layout,
  which is what you want with a keyboard or on a tablet.
- **Restart code-server** — in the overflow menu, for when the remote process wedges.
- **Copy code-server password** — in the overflow menu, if you want to open the same
  instance from a laptop.
- **Links out of the editor** (docs, marketplace) open in your normal browser rather than
  hijacking the editor WebView.

## Firewall

You do **not** need to open port 8080. Only your SSH port needs to be reachable. If you
want to double-check nothing is exposed:

```bash
ss -ltn | grep 8080          # should show 127.0.0.1:8080, not 0.0.0.0:8080
```

If the app detects a non-loopback bind address it shows a red banner above the editor.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| "code-server is not installed" after a successful install | The binary landed in `~/.local/bin` and your non-interactive shell does not have it on `PATH`. The app and script both prepend `$HOME/.local/bin`, but a restrictive `~/.bashrc` can still interfere. |
| Setup fails with "neither curl nor wget" | `apt install curl` (or the distro equivalent) and retry. |
| Editor loads but immediately shows the login page again | Cookies were cleared. The app re-injects the stored password on the login page; use **Copy code-server password** if you need it manually. |
| Tunnel drops after a few minutes idle | The SSH connection sets a 20 s keep-alive. If your network is still dropping it, `ClientAliveInterval` on the VPS `sshd_config` helps too. |
| Editor is very cramped | Switch to the desktop layout in the overflow menu, and rotate to landscape. |

## Security notes

- The SSH private key is encrypted with AES-256-GCM under an Android Keystore master key
  and never leaves the device.
- The code-server password is stored the same way. It is a secondary control: reaching
  the login page at all already requires the SSH key.
- **Host key verification is currently permissive** (`PromiscuousVerifier` in
  `SshRepository`), which means the app does not detect a man-in-the-middle on the SSH
  connection. This predates the VS Code feature and applies to the terminal and monitor
  screens too. Pinning the host key on first connect is the recommended next step.
