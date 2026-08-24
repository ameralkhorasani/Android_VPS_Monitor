/*
 * Bridge between the xterm.js page and the Kotlin TerminalScreen.
 *
 * Kotlin calls into here:  writeTerminalData, applyTerminalTheme, focusTerminal,
 *                          blurTerminal, setFontSize, requestFit, clearTerminal
 * Here calls into Kotlin:  kotlinBridge.sendInput(b64), kotlinBridge.onResize(cols, rows),
 *                          kotlinBridge.onReady()
 */

// If the vendored engine is missing the page must say so rather than presenting a black
// rectangle that swallows every tap.
if (typeof Terminal === 'undefined') {
    document.getElementById('terminal').style.display = 'none';
    document.getElementById('boot-error').style.display = 'block';
    throw new Error('xterm.js failed to load');
}

const OUTPOST_THEMES = {
    dark: {
        background: '#0D0D0D',
        foreground: '#FFFFFF',
        cursor: '#34D399',
        cursorAccent: '#0D0D0D',
        selectionBackground: 'rgba(52, 211, 153, 0.3)',
        black: '#000000',
        red: '#F87171',
        green: '#34D399',
        yellow: '#FBBF24',
        blue: '#60A5FA',
        magenta: '#A78BFA',
        cyan: '#38BDF8',
        white: '#FFFFFF',
        brightBlack: '#6B7280',
        brightRed: '#FCA5A5',
        brightGreen: '#6EE7B7',
        brightYellow: '#FCD34D',
        brightBlue: '#93C5FD',
        brightMagenta: '#C4B5FD',
        brightCyan: '#7DD3FC',
        brightWhite: '#FFFFFF'
    },
    light: {
        background: '#FFFFFF',
        foreground: '#111827',
        cursor: '#059669',
        cursorAccent: '#FFFFFF',
        selectionBackground: 'rgba(5, 150, 105, 0.25)',
        black: '#111827',
        red: '#DC2626',
        green: '#059669',
        yellow: '#B45309',
        blue: '#2563EB',
        magenta: '#7C3AED',
        cyan: '#0891B2',
        white: '#4B5563',
        brightBlack: '#6B7280',
        brightRed: '#EF4444',
        brightGreen: '#10B981',
        brightYellow: '#D97706',
        brightBlue: '#3B82F6',
        brightMagenta: '#8B5CF6',
        brightCyan: '#06B6D4',
        brightWhite: '#111827'
    }
};

const term = new Terminal({
    cursorBlink: true,
    fontSize: 13,
    fontFamily: '"Droid Sans Mono", "Roboto Mono", monospace',
    lineHeight: 1.15,
    scrollback: 5000,
    // Redraw the row under the cursor promptly; the default 0 batches updates in a way
    // that reads as lag when typing over a high-latency SSH link.
    smoothScrollDuration: 0,
    convertEol: false,
    macOptionIsMeta: false,
    theme: OUTPOST_THEMES.dark
});

const fitAddon = new FitAddon.FitAddon();
term.loadAddon(fitAddon);
term.open(document.getElementById('terminal'));

/*
 * Gboard specifics. xterm keeps a hidden textarea as its input target; without these
 * attributes Android runs autocorrect and auto-capitalisation over shell commands and
 * injects backspaces to "fix" them mid-line.
 */
const helper = term.textarea;
if (helper) {
    helper.setAttribute('autocapitalize', 'none');
    helper.setAttribute('autocomplete', 'off');
    helper.setAttribute('autocorrect', 'off');
    helper.setAttribute('spellcheck', 'false');
    helper.setAttribute('inputmode', 'text');
    helper.setAttribute('enterkeyhint', 'enter');
}

// ---------------------------------------------------------------------------
// Focus. A tap anywhere has to put the caret in the helper textarea, otherwise
// Android has no focused input to raise the soft keyboard for.
// ---------------------------------------------------------------------------

function focusTerminal() {
    try {
        term.focus();
        if (helper) helper.focus();
    } catch (e) {}
}

function blurTerminal() {
    try {
        if (helper) helper.blur();
    } catch (e) {}
}

// Selecting text should not steal the tap that raises the keyboard, so only refocus
// when the gesture was a tap rather than a drag.
let touchStartX = 0;
let touchStartY = 0;

document.addEventListener('touchstart', (e) => {
    if (e.touches.length !== 1) return;
    touchStartX = e.touches[0].clientX;
    touchStartY = e.touches[0].clientY;
}, { passive: true });

document.addEventListener('touchend', (e) => {
    const touch = e.changedTouches[0];
    if (!touch) return;
    const moved = Math.abs(touch.clientX - touchStartX) + Math.abs(touch.clientY - touchStartY);
    if (moved < 12 && !term.hasSelection()) {
        focusTerminal();
    }
}, { passive: true });

document.addEventListener('click', () => {
    if (!term.hasSelection()) focusTerminal();
});

// ---------------------------------------------------------------------------
// Geometry. The remote PTY has to be told the real column and row count, or the
// shell keeps line-wrapping at the default 80x24 and long commands smear across
// the display.
// ---------------------------------------------------------------------------

let lastCols = 0;
let lastRows = 0;
let fitTimer = null;

function reportGeometry() {
    if (term.cols === lastCols && term.rows === lastRows) return;
    lastCols = term.cols;
    lastRows = term.rows;
    if (window.kotlinBridge && window.kotlinBridge.onResize) {
        window.kotlinBridge.onResize(term.cols, term.rows);
    }
}

function requestFit() {
    if (fitTimer) clearTimeout(fitTimer);
    // Debounced: the soft keyboard animating into place fires a burst of resizes, and
    // refitting on each one is what makes the view judder.
    fitTimer = setTimeout(() => {
        fitTimer = null;
        try {
            fitAddon.fit();
        } catch (e) {}
        reportGeometry();
    }, 60);
}

window.addEventListener('resize', requestFit);
window.addEventListener('orientationchange', requestFit);

// visualViewport is what actually changes when the keyboard opens over the page.
if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', requestFit);
}

// Catches layout changes the window-level events miss, such as the accessory row
// growing or the Compose parent re-measuring the WebView.
if (window.ResizeObserver) {
    new ResizeObserver(requestFit).observe(document.getElementById('terminal'));
}

// ---------------------------------------------------------------------------
// I/O
// ---------------------------------------------------------------------------

/*
 * Sticky modifiers.
 *
 * A soft keyboard has no Ctrl or Alt, and the accessory row cannot send one on its own
 * because the modifier has to combine with whatever character is typed next. Arming the
 * modifier here and applying it to the next keystroke gives real Ctrl+C, Ctrl+Z, Ctrl+L,
 * Ctrl+R and Alt+<key> from an on-screen keyboard.
 */
let pendingCtrl = false;
let pendingAlt = false;

function setPendingCtrl(on) {
    pendingCtrl = !!on;
    notifyModifiers();
}

function setPendingAlt(on) {
    pendingAlt = !!on;
    notifyModifiers();
}

function notifyModifiers() {
    if (window.kotlinBridge && window.kotlinBridge.onModifiersChanged) {
        window.kotlinBridge.onModifiersChanged(pendingCtrl, pendingAlt);
    }
}

function applyModifiers(data) {
    // The common case is no modifier armed, and it must not cost a bridge call per
    // keystroke.
    if (!pendingCtrl && !pendingAlt) return data;

    let out = data;

    if (pendingCtrl) {
        pendingCtrl = false;
        if (out.length === 1) {
            const code = out.toUpperCase().charCodeAt(0);
            // Ctrl+@ through Ctrl+_ are the printable range minus 64.
            if (code >= 64 && code <= 95) {
                out = String.fromCharCode(code - 64);
            } else if (code === 32) {
                out = '\x00'; // Ctrl+Space is NUL
            }
        }
    }

    if (pendingAlt) {
        pendingAlt = false;
        // Alt is transmitted as an ESC prefix, which is how every terminal does it.
        out = '\x1b' + out;
    }

    notifyModifiers();
    return out;
}

term.onData((data) => {
    if (window.kotlinBridge && window.kotlinBridge.sendInput) {
        window.kotlinBridge.sendInput(encodeBase64(applyModifiers(data)));
    }
});

// Covers the sequences xterm reports separately from onData, notably the ones some
// hardware and Bluetooth keyboards emit.
term.onBinary((data) => {
    if (window.kotlinBridge && window.kotlinBridge.sendInput) {
        let bytes = '';
        for (let i = 0; i < data.length; i++) bytes += data.charAt(i);
        window.kotlinBridge.sendInput(btoa(bytes));
    }
});

function encodeBase64(text) {
    return btoa(unescape(encodeURIComponent(text)));
}

function decodeBase64(b64) {
    return decodeURIComponent(escape(atob(b64)));
}

function writeTerminalData(base64Data) {
    try {
        term.write(decodeBase64(base64Data));
    } catch (e) {
        console.error('Terminal write error: ', e);
    }
}

function clearTerminal() {
    term.clear();
}

// ---------------------------------------------------------------------------
// Appearance
// ---------------------------------------------------------------------------

function applyTerminalTheme(mode) {
    const theme = OUTPOST_THEMES[mode] || OUTPOST_THEMES.dark;
    try {
        term.options.theme = theme;
    } catch (e) {
        // xterm < 5 exposed this through setOption instead.
        if (term.setOption) term.setOption('theme', theme);
    }
    document.documentElement.style.backgroundColor = theme.background;
    document.body.style.backgroundColor = theme.background;
}

function setFontSize(px) {
    const size = Math.max(8, Math.min(24, px));
    try {
        term.options.fontSize = size;
    } catch (e) {
        if (term.setOption) term.setOption('fontSize', size);
    }
    requestFit();
}

// Pinch to zoom the font, which is the gesture people reach for on a phone.
let pinchStartDistance = 0;
let pinchStartSize = 13;

document.addEventListener('touchstart', (e) => {
    if (e.touches.length !== 2) return;
    pinchStartDistance = touchDistance(e.touches);
    pinchStartSize = term.options.fontSize || 13;
}, { passive: true });

document.addEventListener('touchmove', (e) => {
    if (e.touches.length !== 2 || pinchStartDistance <= 0) return;
    const ratio = touchDistance(e.touches) / pinchStartDistance;
    setFontSize(Math.round(pinchStartSize * ratio));
}, { passive: true });

function touchDistance(touches) {
    const dx = touches[0].clientX - touches[1].clientX;
    const dy = touches[0].clientY - touches[1].clientY;
    return Math.sqrt(dx * dx + dy * dy);
}

// ---------------------------------------------------------------------------
// Ready
// ---------------------------------------------------------------------------

fitAddon.fit();
reportGeometry();
focusTerminal();

if (window.kotlinBridge && window.kotlinBridge.onReady) {
    window.kotlinBridge.onReady();
}
