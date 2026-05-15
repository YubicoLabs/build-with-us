<p align="center">
  <img src="images/hero-image.png" alt="Yubico Early Access Program Hero Image">
</p>

<h1 align="center">Yubico Early Access Program: Firmware 5.8.0 Beta 🚀</h1>

Welcome to the Yubico Early Access Program (EAP) developer repository. This space is dedicated to developers and security engineers who are ready to build, test, and integrate with the next generation of hardware security.

We are currently previewing **Firmware 5.8.0**, a major update to the YubiKey 5 Series that introduces critical cryptographic enhancements and expanded storage capabilities.

---

## What’s New in 5.8.0?

This firmware update isn't just a patch; it’s a toolkit expansion for modern authentication workflows.

### 🔐 Asynchronous Remote Key Generation (ARKG)
ARKG allows applications to generate public keys cryptographically linked to a credential on a YubiKey without having said YubiKey present. Only on signature creation the YubiKey is needed.

### 🧱 Persistent PIN User Access Token (PPUAT)
YubiKeys with firmware version 5.8 and later support CTAP 2.2's Persistent PinUvAuthToken (PPUAT)
A new type of access token that can be acquired via PIN Protocol v2.

PPUATs enable a better user experience by allowing applications to list discoverable credentials from YubiKeys without requiring repeated PIN entry.


### 📈 Expanded Storage & Logic
* **FIDO2:** Increased capacity for up to 100 resident keys (discoverable credentials).
* **OATH:** Expanded storage for up to 64 TOTP/HOTP seeds.
* **Key Management:** Support for "Move" and "Copy" operations for certain credential types.
* **RSA 3072/4096:** Native support for longer RSA keys to meet evolving compliance standards.

---

## 🛠 Developer Onboarding & Demos

We have compiled a set of implementation examples to help you integrate these new primitives into your applications. 

Visit the [**/quickstart**](./quickstart) directory for platform-specific demos:

* [**iOS Implementation**](./quickstart/ios) - Utilizing the Yubico Mobile SDK for iOS.
* [**Android Implementation**](./quickstart/android) - Native Android integration examples.
* [**.NET / Desktop**](./quickstart/dotnet) - Implementation using our desktop-class libraries.

---

## 🚀 Getting Started

1.  **Hardware:** Ensure you have a YubiKey provided through the Yubico Early Access Program ("dev" or "eap" edged on the case of it)
2.  **SDKs:** Use the latest versions of our SDKs (available on NuGet, CocoaPods, and Maven) that support the 5.8.0 command sets.
3.  **Explore:** Dive into the `/quickstart` folder and start building.

---

## 📝 Feedback & Contributions

This is a **Beta** release. Your feedback is what makes our hardware better.
* Found a bug in the demo code? Open an [**Issue**](https://github.com/yubicolabs/early-access-program/issues/new?title=[Beta%20Feedback]%20&body=Please%20describe%20the%20issue:).
* Have an optimization for the implementation? Submit a [**Pull Request**](https://github.com/yubicolabs/early-access-program/pulls/new).
* General firmware feedback? Please use the official EAP email yubico com unicated.
* Want to join this program? Fill out the application form under https://www.yubico.com/yubikey-5-8-early-access.

## ⚖️ Disclaimer
*This firmware is for testing purposes only. Beta hardware should not be used for primary production credentials, as beta firmware is not eligible for long-term security guarantees or field updates. Same is true for any software discussed here: Those are in a beta state and will be subject to change without notice.*

---
**Built by Developers, for Developers.** [Yubico Developer Portal](https://developers.yubico.com/) | [Privacy Policy](https://www.yubico.com/support/terms-conditions/privacy-policy/)
