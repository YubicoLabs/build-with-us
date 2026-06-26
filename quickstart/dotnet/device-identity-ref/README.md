# Silent passkey autofill: conditional mediation for security keys

A WPF reference app for the YubiKey firmware 5.8 conditional-mediation features
(PPUAT, PCMR, encIdentifier, encCredStoreState), built around one scenario:
silent passkey autofill that survives an app restart.

Plug in the key, enter the PIN once, and check "Keep read-only access on this PC".
Your passkeys appear. Close the app, reopen it, and they appear again with no PIN
prompt. The app reloads a persistent token (PPUAT) that it encrypted to disk the
first time.

## What conditional mediation is

When a user taps a username field, the browser can offer stored passkeys in the
autofill dropdown; picking one signs the user in. The WebAuthn mechanism behind
this, a credential request that waits quietly instead of opening a modal dialog,
is called conditional mediation.

Those autofill suggestions can only include passkeys the platform can list
without bothering the user. Software passkey providers can do that. Security
keys could not, because reading their credential list required a PIN entry
every time. The YubiKey 5.8 features in this app remove that limitation: one
PIN grants a long-lived read-only token (PPUAT), and from then on the platform
can list the key's credentials silently and offer them the same way it offers
software passkeys.

## What it demonstrates

| Feature | Where it shows up |
|---|---|
| PPUAT (long-lived read-only auth token) | One PIN, then silent reads, persisted across restarts |
| PCMR (persistent read-only permission) | "Test read-only scope" tries a credential delete and shows the key rejecting it |
| encIdentifier (stable, private device id) | Device ID row in the key details card. The raw value rotates on every `getInfo`, so only a PPUAT holder can resolve it |
| encCredStoreState (change signal) | Status chip under the passkey list: "Served from cache" when unchanged (re-enumeration skipped), "Re-read from key" when it changed |

## How the PPUAT lifecycle works

- Created on unlock only if the user opts in, with the
  `PersistentCredentialManagementReadOnly` permission.
- Stored keyed by the key's decrypted device id and encrypted at rest. This app
  uses Windows DPAPI (`ProtectedData`, scoped to the current Windows user).
- Validated on reconnect by decrypting `encIdentifier` with each stored token
  (`AuthenticatorInfo.GetIdentifier`) and matching the result against the
  connected key.
- Invalidated by the key when the FIDO2 PIN changes or the key is reset. The app
  falls back to PIN entry when that happens.

## The one file to copy

[`PasskeyAutofill.Core/PasskeyVault.cs`](PasskeyAutofill.Core/PasskeyVault.cs) is
the whole pattern in one class, with no UI or storage dependencies:

```csharp
var vault = new PasskeyVault(store); // store is your IPpuatStore (DPAPI, keychain, etc.)

// On connect, try silent first (in-memory token, then persisted tokens).
VaultResult result = vault.Read(key);
if (result.Status == VaultStatus.NeedsPin)
{
    // First time for this key: one PIN entry. If the user opts in, the token
    // is persisted so the next app launch is silent too.
    result = vault.Unlock(key, pinEntry);
}

// result.Passkeys is ready to show; result.Identity carries the identity snapshot.

vault.SignIn(key, passkey, pinEntry); // real assertion, verified locally (touch the key)
vault.SignOut();                      // forget this PC: zero memory, erase storage
```

`PasskeyVault` depends only on `Yubico.YubiKey` and two interfaces you implement:
`IPinEntry` (PIN collection) and `IPpuatStore` (token persistence). Persistence is
deliberately not part of `.Core`: lift `.Core` into a WinForms, WinUI, or service
app and supply your platform's secret store. The WPF app's
[`DpapiPpuatStore`](PasskeyAutofill.Wpf/Services/DpapiPpuatStore.cs) is the
reference implementation.

## Project layout

```
PasskeyAutofill.Core         no UI or storage dependencies; the copy-paste layer
├── PasskeyVault.cs          PPUAT lifecycle: Read, Unlock, SignIn, SignOut
├── IPpuatStore.cs           token persistence contract (platform implements it)
├── IPinEntry.cs             PIN + persist-choice contract (UI implements it)
├── PinKeyCollector.cs       bridges the SDK KeyCollector to IPinEntry
├── Passkey.cs               UI-ready credential DTO (includes thirdPartyPayment)
├── DeviceIdentity.cs        UI-ready snapshot of the 5.8 conditional-mediation fields
├── ScopeProbeResult.cs      result of the read-only scope check
├── SignInResult.cs          result of an assertion verified locally
├── VaultResult.cs           result of a read (passkeys, identity, cache hit)
└── VaultStatus.cs           Ready / NeedsPin / Unsupported / Error

PasskeyAutofill.Wpf          presentation only
├── MainWindow.xaml          state-driven screens, passkey list, key details card
├── ViewModels/MainViewModel.cs      the state machine
├── Services/KeyMonitor.cs           plug/unplug events on the UI thread
├── Services/DpapiPpuatStore.cs      IPpuatStore via Windows DPAPI
└── Services/PinService.cs           IPinEntry backed by the PIN field on the locked screen
```

PIN entry is inline on the locked screen: a PIN
field, the persist checkbox, and an Unlock button. A wrong PIN shows the
remaining tries under the field, and a persistent warning appears when three or
fewer tries remain. While the key waits for a touch, the busy screen says so.

## How this maps to passkey UX practices

Follows [YubicoLabs/passkey-ux](https://github.com/YubicoLabs/passkey-ux). That
spec targets the browser surface; the principles carry over to this native
autofill scenario.

| Principle | How this app applies it |
|---|---|
| AP-01, never dead-end | Every failure (stale token, wrong key, no PIN set) routes back to PIN entry or a retry |
| AP-05, silent plus explicit affordance | Reconnect autofills silently; the locked screen always offers PIN entry |
| AP-10, positive framing | The persist choice is stated plainly: "Keep read-only access on this PC" |
| 6.6, actionable errors | Errors are plain language with a next step, never raw CTAP codes |
| 7.6, PPUAT autofill | One PIN acquires the PPUAT; it is reused silently across restarts |
| Consent | Persistence is opt-in and reversible ("Forget this PC") |
| Security hygiene | The PPUAT is encrypted at rest and zeroed in memory on exit |

Why native and not a browser: PPUAT, `encIdentifier`, and `encCredStoreState`
are CTAP platform-layer primitives with no `navigator.credentials` surface, so
only native code talking CTAP2 through the Yubico SDK can build this today.
This app plays the role the platform (Windows Hello, a password manager) would
play.

## Run it

Requires a physical YubiKey with firmware 5.8+, a FIDO2 PIN set, and an elevated
session (the app manifest requests admin for USB FIDO2 access).

```
# from this folder, in Visual Studio: set PasskeyAutofill.Wpf as startup, F5
# or:
dotnet run --project PasskeyAutofill.Wpf
```

1. Launch with no key: "Insert your security key."
2. Plug in the key, enter the PIN, check "Keep read-only access on this PC",
   click Unlock. Your passkeys appear and the key details card fills in.
3. Close the app and reopen it. Passkeys appear with no PIN prompt.
4. Unplug and replug: silent re-read. The chip under the list reads "Served
   from cache" when nothing changed. Add or remove a credential elsewhere and
   reconnect: "Re-read from key".
5. Click Sign in on a passkey and touch the key when it blinks: "Signed in,
   verified locally." If the app unlocked silently this session, it asks for
   your PIN once and then continues the sign-in: the read-only token can list
   passkeys but cannot authorize an assertion.
6. Test read-only scope: the delete is rejected because the PPUAT is read-only.
7. Forget this PC: the next connect requires a PIN again. Changing the FIDO2 PIN
   on the key also invalidates the stored token.

## SDK note

Uses the released [`Yubico.YubiKey`](https://www.nuget.org/packages/Yubico.YubiKey)
package, version 1.17.1 or later, which includes the 5.8 conditional-mediation
support
