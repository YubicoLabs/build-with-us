# ⚡ Quickstart Guide: Firmware 5.8.0 Integrations

Welcome to the implementation phase of the Yubico Early Access Program. This directory contains boilerplate code, sample projects, and implementation logic for the two core pillars of the 5.8.0 firmware update: **ARKG** and **PUAT**.

These demos are designed to be "plug-and-play" so you can test hardware behavior before writing your production logic.

---

## 🛠 Prerequisites

Before diving into the code, ensure you have:
1.  **5.8.0 EAP Hardware:** A YubiKey with the 5.8.0 beta firmware.
2.  **Yubico Authenticator**: [macOS](https://apps.apple.com/us/app/yubico-authenticator/id1497506650?mt=12) | [Windows](https://apps.microsoft.com/detail/9nfng39387k0?hl=en-US&gl=US)
3.  **Latest SDK (Pre) releases:**
    * **iOS:** Yubikit 5.0.0-beta+
    * **Android:** Yubikit 3.1.0
    * **Desktop:** .NET SDK 1.10.0-beta+

---

## 🏗 Feature 1: Attestation of RSA Key Generation (ARKG)

ARKG creates public keys without a YubiKey attached. Please follow the encompassed code.

Important files and folders:
| Platform | Path | Primary Class/Method |
| :--- | :--- | :--- |
| **iOS** | [`/ios`](./ios) | `YKFKeyAttestationTask` |
| **Android** | [`/android`](./android) | `ArkgSession.java` |
| **.NET** | [`/arkg-dotnet`](./dotnet) | `YubiKeyDevice.CreateArkgAttestation()` |

**Key Developer Task:** Review the `createCredentials` and `assertCredentials` logic in these demos to see how the different parts play together.

---

## 👤 Feature 2: Physical User Auth Template (PUAT)

PUAT allows you to define complex "User Presence" rules. You can now programmatically require a touch, a PIN, or a biometric match for specific cryptographic slots.

| Platform | Path | Focus Area |
| :--- | :--- | :--- |
| **iOS** | [`/ios`](./ios) | UI-triggered hardware touch prompts. |
| **Android** | [`/android`](./android) | Managing template persistence. |
| **.NET** | [`/dotnet`](./dotnet) | Defining custom auth policies via CLI/SDK. |

**Key Developer Task:** Check the policy definitions in the `Config` files to see how to toggle `TouchPolicy` and `PinPolicy` flags.

---

## 🚀 How to use these demos

1.  **Clone** this repository.
2.  Navigate to the specific platform folder (e.g., `cd quickstart/dotnet`).
3.  Follow the local `README.md` in that sub-folder for build instructions (CocoaPods, Gradle, or NuGet setup).
4.  Plug in your 5.8.0 YubiKey and run the "Debug" target.

---

## 🐞 Encountered an Integration Bug?

If the sample code isn't behaving as expected with your EAP hardware, please let us know immediately.

[👉 Report a Quickstart Bug][new-issue]

[new-issue]: https://github.com/yubicolabs/early-access-program/issues/new?title=[Quickstart%20Bug]%20&labels=bug,quickstart,5.8.0&body=**Platform:**%20(iOS/Android/Dotnet)%0A**Issue:**%20Describe%20what%20happened.
