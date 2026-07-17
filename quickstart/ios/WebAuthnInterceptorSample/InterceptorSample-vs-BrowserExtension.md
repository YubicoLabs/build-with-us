# WebAuthnInterceptorSample vs. Safari Browser Extension

A comparison of the in-app `WKWebView` interception approach used by `WebAuthnInterceptorSample` versus building the same functionality as a Safari Web Extension.

---

## Architecture recap: what the sample actually does

A native Swift app wraps a `WKWebView`. `Interceptor.js` is injected at document start and monkey-patches `navigator.credentials.create()` / `navigator.credentials.get()`. The patched functions serialize the request to JSON and post it to Swift via `webkit.messageHandlers`. Swift talks to the YubiKey through the YubiKit Swift SDK (NFC, USB-C, or Lightning), then posts the JSON credential back into the page via `evaluateJavaScript`.

---

## Side-by-side comparison

| Dimension | InterceptorSample (in-app WKWebView) | Safari Web Extension |
|---|---|---|
| Where users browse | Your custom app only — they have to leave Safari | Their normal Safari (major UX win) |
| Hardware access | Full: `CoreNFC`, `ExternalAccessory` (Lightning), USB-C, YubiKit Swift SDK directly | None. Web extensions run in a sandboxed JS context with `browser.*` APIs only — no NFC, no ExternalAccessory, no USB |
| WebAuthn interception | Inject `WKUserScript` at `documentStart` — clean, works on any site loaded in the app | `content_scripts` can inject, but timing vs. site scripts is fragile; some sites detect monkey-patching |
| Distribution | App Store as a normal app | App Store as containing app + extension bundle |
| Cross-site behavior | Only works inside your app | Works on every site the user visits in Safari |
| Extensions (PRF, previewSign) | Full control via YubiKit | Same control *if* you can reach a YubiKey — which on iOS you cannot |

---

## Could `WebAuthnInterceptorSample` code become a Safari Web Extension?

### The JavaScript half — yes, mostly portable

`Interceptor.js` (the monkey-patch, JSON ↔ credential serialization, and PRF / previewSign decoding) would drop into a Safari Web Extension `content_script` with minor changes. The main edit: replace

```js
window.webkit.messageHandlers.__webauthn_create__.postMessage(...)
```

with

```js
browser.runtime.sendMessage({ type: 'create', ... })
```

and have the extension background service worker forward the request onward.

### The Swift half — no, not from inside the extension

**iOS.** Safari Web Extensions on iOS run inside an app extension process. They cannot use `CoreNFC` (no extension entitlement), cannot use `ExternalAccessory`, and cannot reach USB. A pure extension cannot talk to a YubiKey on iPhone/iPad at all. The only workaround is:

> Extension forwards request → containing app via App Group / custom URL scheme / `NSXPCConnection` → app does the YubiKit work → returns result.

That round-trip means foregrounding your app on every WebAuthn ceremony, which defeats the purpose of using the extension.

**macOS.** Much more feasible. The extension can communicate with a Native Messaging Host (a regular macOS helper binary) over stdio. The host links YubiKit and owns the NFC / USB / Lightning connection. This is the cleanest cross-Safari path. PIN entry and NFC scan UI would live in the host app, not in browser chrome.

---

## Pros and cons

### Browser extension — pros

- Works in the user's real browser on every site — no behavior change for them
- One install covers all WebAuthn ceremonies system-wide (on macOS)
- No need to convince users to "use our browser app"

### Browser extension — cons

- iOS is effectively a non-starter for direct hardware access; any iOS build is a hybrid that still needs the containing app to do the real work, with poor UX
- Safari extensions can be suspended aggressively; mid-ceremony state is harder to keep alive
- Message-passing hops (content → background → native host → YubiKit) add latency and failure modes
- App Store / notarization friction for the native messaging host
- Users can disable the extension per-site, breaking flows silently

### InterceptorSample — pros

- One process, direct YubiKit access, no IPC — simplest and fastest
- Works identically on iOS and macOS
- Full control over PIN UI, NFC dialog, and connection racing
- Easy to ship extensions (PRF, previewSign) because you own both sides of the boundary

### InterceptorSample — cons

- Users must browse inside *your* app — you are competing with Safari for the address bar
- No coverage of arbitrary sites unless the user explicitly opens them in your app
- `WKWebView` is not Safari: different cookie jar, no shared logins, no Safari extensions, no Reader mode, etc.

---

## Recommendation

For **iOS**, the sample's in-app `WKWebView` approach is the only realistic shape today. A Safari Web Extension on iOS cannot reach a YubiKey without bouncing through a containing app, which is worse than what `WebAuthnInterceptorSample` already provides.

For **macOS**, a Safari Web Extension plus a Native Messaging Host that links YubiKit is a genuine win and is worth prototyping. The JavaScript in `Interceptor.js` is roughly 90% reusable, and the Swift in `WebAuthnHandler.swift` moves into the native host largely unchanged.
