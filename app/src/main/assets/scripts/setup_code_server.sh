#!/usr/bin/env bash
#
# Installs and configures code-server (VS Code in the browser) on a VPS so that the
# Outpost Android app can reach it through an SSH port-forward.
#
# code-server is bound to 127.0.0.1 only. It is never exposed to the public internet;
# the app reaches it exclusively through the SSH tunnel, authenticated by the SSH key.
#
# Usage:  setup_code_server.sh [PORT] [PASSWORD]
#   PORT      port on the VPS loopback interface (default 8080)
#   PASSWORD  code-server login password (default: reuse existing, else generate)
#
# The app runs this over SSH and parses the OUTPOST_RESULT_* lines at the end.

set -u

PORT="${1:-8080}"
PASSWORD="${2:-}"

log() { echo "[outpost] $*"; }
fail() { echo "[outpost] ERROR: $*"; echo "OUTPOST_RESULT_OK=0"; exit 1; }

export PATH="$HOME/.local/bin:/usr/local/bin:$PATH"

# ---------------------------------------------------------------------------
# 1. Install code-server if it is not already present
# ---------------------------------------------------------------------------
if command -v code-server >/dev/null 2>&1; then
    log "code-server already installed: $(code-server --version 2>/dev/null | head -n1)"
else
    log "Installing code-server..."

    # The official installer needs root to install system-wide. Without it, fall
    # back to a self-contained install under $HOME/.local.
    if [ "$(id -u)" -eq 0 ] || sudo -n true 2>/dev/null; then
        INSTALL_ARGS=""
    else
        log "No root/sudo available - using standalone install in \$HOME/.local"
        INSTALL_ARGS="--method standalone --prefix $HOME/.local"
    fi

    if command -v curl >/dev/null 2>&1; then
        # shellcheck disable=SC2086
        curl -fsSL https://code-server.dev/install.sh | sh -s -- $INSTALL_ARGS 2>&1 | sed 's/^/[install] /'
    elif command -v wget >/dev/null 2>&1; then
        # shellcheck disable=SC2086
        wget -qO- https://code-server.dev/install.sh | sh -s -- $INSTALL_ARGS 2>&1 | sed 's/^/[install] /'
    else
        fail "neither curl nor wget is installed on this server"
    fi

    export PATH="$HOME/.local/bin:$PATH"
    command -v code-server >/dev/null 2>&1 || fail "code-server installation failed"
    log "Installed: $(code-server --version 2>/dev/null | head -n1)"
fi

# ---------------------------------------------------------------------------
# 2. Write the config: loopback bind + password auth
# ---------------------------------------------------------------------------
CONFIG_DIR="$HOME/.config/code-server"
CONFIG="$CONFIG_DIR/config.yaml"
mkdir -p "$CONFIG_DIR" || fail "cannot create $CONFIG_DIR"

if [ -z "$PASSWORD" ]; then
    if [ -f "$CONFIG" ] && grep -q '^password:' "$CONFIG"; then
        PASSWORD="$(grep '^password:' "$CONFIG" | head -n1 | sed 's/^password:[[:space:]]*//' | tr -d '\r')"
        log "Reusing the existing code-server password"
    fi
fi

if [ -z "$PASSWORD" ]; then
    if command -v openssl >/dev/null 2>&1; then
        PASSWORD="$(openssl rand -hex 16)"
    else
        PASSWORD="$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n' | cut -c1-32)"
    fi
    log "Generated a new code-server password"
fi

cat > "$CONFIG" <<EOF
bind-addr: 127.0.0.1:$PORT
auth: password
password: $PASSWORD
cert: false
EOF
chmod 600 "$CONFIG"
log "Wrote $CONFIG (bound to 127.0.0.1:$PORT)"

# ---------------------------------------------------------------------------
# 3. Start it, preferring systemd so it survives reboots
# ---------------------------------------------------------------------------
SERVICE_USER="$(id -un)"
STARTED=""

if command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]; then
    if [ "$(id -u)" -eq 0 ]; then
        SYSTEMCTL="systemctl"
    elif sudo -n true 2>/dev/null; then
        SYSTEMCTL="sudo systemctl"
    else
        SYSTEMCTL=""
    fi

    if [ -n "$SYSTEMCTL" ] && { [ -f /lib/systemd/system/code-server@.service ] || [ -f /usr/lib/systemd/system/code-server@.service ]; }; then
        log "Enabling systemd service code-server@$SERVICE_USER"
        if $SYSTEMCTL enable "code-server@$SERVICE_USER" >/dev/null 2>&1 && \
           $SYSTEMCTL restart "code-server@$SERVICE_USER" >/dev/null 2>&1; then
            STARTED="systemd"
        else
            log "systemd start failed, falling back to a background process"
        fi
    fi
fi

if [ -z "$STARTED" ]; then
    log "Starting code-server as a background process"
    pkill -u "$(id -u)" -f 'code-server.*--bind-addr' >/dev/null 2>&1
    sleep 1
    nohup code-server --bind-addr "127.0.0.1:$PORT" > "$HOME/.code-server.log" 2>&1 &
    STARTED="nohup"
    log "Logs: $HOME/.code-server.log (note: will not survive a reboot)"
fi

# ---------------------------------------------------------------------------
# 4. Wait until the port is actually accepting connections
# ---------------------------------------------------------------------------
READY=0
for _ in $(seq 1 30); do
    if (ss -ltn 2>/dev/null || netstat -ltn 2>/dev/null) | grep -q "127.0.0.1:$PORT[^0-9]"; then
        READY=1
        break
    fi
    sleep 1
done

if [ "$READY" -ne 1 ]; then
    log "code-server did not start listening on 127.0.0.1:$PORT within 30s"
    if [ "$STARTED" = "systemd" ]; then
        log "Check: journalctl -u code-server@$SERVICE_USER -n 50"
    else
        tail -n 20 "$HOME/.code-server.log" 2>/dev/null | sed 's/^/[log] /'
    fi
    fail "code-server is not listening"
fi

log "code-server is listening on 127.0.0.1:$PORT (started via $STARTED)"
echo "OUTPOST_RESULT_PASSWORD=$PASSWORD"
echo "OUTPOST_RESULT_PORT=$PORT"
echo "OUTPOST_RESULT_OK=1"
