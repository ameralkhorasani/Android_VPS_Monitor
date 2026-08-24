# Reaching a VPS service from the phone's browser

You have something running on the VPS — a dev server, a dashboard, an admin panel — on
port 8090. You want to open it in Chrome on your phone. This is how.

## The short version

1. Open the app, find the server card, tap the **⇄ (Port Forwards)** icon.
2. Tap **+**.
3. Remote port: `8090`. Local port fills itself in as `8090`. Tap **Add**.
4. Tap **Start**, then **Open in browser**.

Chrome opens `http://localhost:8090` and it works. Nothing on the VPS was exposed to the
internet, and no firewall port was opened.

## Why localhost on the phone reaches the VPS

```
Phone                                        VPS
┌──────────────────────────────┐             ┌────────────────────────────┐
│ Chrome → http://localhost:8090│            │                            │
│              │                │            │  your service              │
│              ▼                │            │  127.0.0.1:8090            │
│ Outpost listening on          │            │          ▲                 │
│ 127.0.0.1:8090                │══ SSH ════╪══════════╝                 │
│ (TunnelService)               │  encrypted │                            │
└──────────────────────────────┘             └────────────────────────────┘
```

The app opens a listening socket on the phone's own loopback interface. Loopback is
per-device, not per-app: every app on the phone, Chrome included, can connect to it, while
nothing on your Wi-Fi network can. Bytes arriving there are carried inside the existing SSH
connection and handed to the VPS, which dials `127.0.0.1:8090` from its own side.

This is exactly `ssh -L 8090:127.0.0.1:8090 your-username@your.vps.example.com`, run from a phone.

## Field by field

| Field | What it means |
| --- | --- |
| **Name** | Label for the list. Defaults to "Port 8090". |
| **Remote port** | The port on the VPS. What your service listens on. |
| **Local port** | The port on the phone — the number you type in the browser. Mirrors the remote port until you edit it. |
| **Bind address on the VPS** | Who the VPS dials on your behalf. `127.0.0.1` for a service on the VPS itself; a LAN address or container IP reaches something else the VPS can see. |
| **Open automatically** | Reopens this forward whenever the Ports screen loads. |

### Local ports below 1024 will not work

Android does not let an ordinary app listen on a privileged port. A service on the VPS's
port 80 or 443 is still reachable — give it a local port above 1024:

```
Remote port 443  →  Local port 8443  →  browse to http://localhost:8443
```

The app rejects local ports under 1024 up front rather than letting the bind fail later
with a message that reads like a bug.

## While you are in the browser

Starting a forward starts a foreground service, and you get a notification saying how many
tunnels are open. That notification is not decoration: it is what stops Android from
killing the app — and your listening socket — the moment you switch to Chrome. Its
**Stop all** action closes every tunnel.

On Android 13 and later the app asks for notification permission the first time you open
the Ports screen. Declining does not break the tunnel, but you lose the "Stop all" control
and any indication that a tunnel is still open.

## Several forwards at once

Forwards to the same server share one SSH connection, which is dropped when the last one
closes. Each needs a distinct local port; the app will not let you save a duplicate.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| "Local port 8090 is already taken on this phone" | Something else on the phone holds it, most often a forward that is still running. Stop it, or pick a different local port. |
| Browser says "connection refused" but the forward is Started | The tunnel is fine; nothing is answering on the far end. Check the service is up on the VPS and bound to the address in **Bind address on the VPS**: `ss -ltn \| grep 8090`. |
| Service is bound to `::1` only | Set the bind address to `::1` instead of `127.0.0.1`. |
| Tunnel dies after switching apps | The notification was dismissed or notification permission was denied, so the service could not stay in the foreground. Grant it in Android Settings → Apps → Outpost → Notifications. |
| Page loads but assets 404 or redirect to a public hostname | The app on the VPS is generating absolute URLs from a configured hostname. That is a configuration issue in that app, not in the tunnel. |

## Security

The listening socket is bound to `127.0.0.1`, never `0.0.0.0`, so nothing else on the
network can reach it — only apps on the phone. The traffic between phone and VPS is inside
the SSH connection, authenticated with the same key the terminal uses, which stays
encrypted under an Android Keystore master key.

`network_security_config.xml` permits cleartext to loopback only, so `http://localhost` is
allowed in-app while everything on the open network still requires TLS.

One caveat carried over from the rest of the app: **host key verification is currently
permissive** (`PromiscuousVerifier` in `SshRepository`), so the app does not detect a
man-in-the-middle on the SSH connection itself. Pinning the host key on first connect is
the recommended next step, and it applies to the terminal and monitor screens too.
