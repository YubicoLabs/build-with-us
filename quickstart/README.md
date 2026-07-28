# ⚡ Quickstart Guide: Firmware 5.8.0 Integrations

Welcome to the implementation phase of the YubiKey 5.8 firmware integrations. This directory contains boilerplate code, sample projects, and implementation logic for the two core pillars of the 5.8 firmware update: **Preview Signing (including ARKG)** and **PPUAT**.

These demos are designed to be "plug-and-play" so you can test hardware behavior before writing your production logic.

---

## 🛠 Prerequisites

Before diving into the code, ensure you have:
1.  **YubiKey 5.8:** A YubiKey with the 5.8 firmware.
2.  **Yubico Authenticator**: [macOS](https://apps.apple.com/us/app/yubico-authenticator/id1497506650?mt=12) | [Windows](https://apps.microsoft.com/detail/9nfng39387k0?hl=en-US&gl=US)
3.  **Latest SDK releases:**
    * **iOS:** [Yubikit-swift 1.3.0](https://github.com/Yubico/yubikit-swift/releases/tag/v1.3.0)
    * **Android:** Yubikit 3.1.0
    * **Desktop:** Yubico.YubiKey 1.17.0+ (.NET 8+)
    * **Python:** Python 3.10+

---

## 🏗 Core Feature 1: Signing Extension Preview w/ Asynchronous Remote Key Generation (ARKG)

The YubiKey 5.8 now offers preview API support for the emerging WebAuthn signing extension with a focus on hardware-backed ECDSA P-256 signatures, digital wallet features, and AI-based workflows. In addition, 5.8 includes developer preview of Asynchronous Remote Key Generation (ARKG) extension. ARKG allows the security key to dynamically generate unique, public keys for distinct workflows. NOTE: This is a separate credential type that cannot be used for authentication.

Important files and folders:
| Platform | Path | Primary Class/Method |
| :--- | :--- | :--- |
| **iOS / macOS** | [`/ios/ARKGPreviewSign`](./ios/ARKGPreviewSign) |  |
| **Android** | [`/android`](./android) | `ArkgSession.java` |
| **.NET** | [`/arkg-dotnet`](./dotnet) | `YubiKeyDevice.CreateArkgAttestation()` |
| **Python** | [`/python`](./python) | [`example_arkg.py`](./python/example_arkg.py) |


---

## 👤 Core Feature 2: Device Identity - Using Persistent PIN User Access Token (PPUAT)

YubiKey 5.8 implements the latest CTAP 2.3 UX enhancements, including Persistent PIN/User Verification Auth Token (PPUAT) protocol feature. This mechanism allows your applications to discover discoverable credentials stored on the hardware key smoothly and intuitively. For the end user, this translates to a streamlined, autofill-like passkey experience inside native apps without repeated PIN entry.

| Platform | Path
| :--- | :--- 
| **iOS / macOS** | [`/ios/device-identity`](./ios/device-identity) 
| **Android** | [`/android`](./android) 
| **.NET** | [`/dotnet`](./dotnet) 
| **Python** | [`/python`](./python)

---

## 🚀 How to use these demos

1.  **Clone** this repository.
2.  Navigate to the specific platform folder (e.g., `cd quickstart/dotnet`).
3.  Follow the local `README.md` in that sub-folder for build instructions (Swift Package Manager, Gradle, or NuGet setup).

---

## 🐞 Encountered an Integration Bug?

If the sample code isn't behaving as expected with your EAP hardware, please let us know immediately.

[👉 Report a Quickstart Bug][new-issue]

[new-issue]: https://github.com/yubicolabs/early-access-program/issues/new?title=[Quickstart%20Bug]%20&labels=bug,quickstart,5.8.0&body=**Platform:**%20(iOS/Android/Dotnet)%0A**Issue:**%20Describe%20what%20happened.
