# 🤖 Yubico 5.8.0 Android Integration Demo

This directory contains a unified Android application designed to showcase the new capabilities of the **Firmware 5.8.0 Beta**. Instead of multiple small samples, this app provides a single interface to test and verify ARKG, PUAT, and expanded storage logic.

## 📱 App Overview

The demo app is built using **Kotlin** and utilizes the latest **YubiKit for Android**. It serves as a reference implementation for:

-   **ARKG (RSA Key Generation & Attestation):** Generate RSA 3072/4096 keys on the YubiKey and retrieve the attestation certificate.
<!-- SOON
 -   **PUAT (Physical User Auth Template):** Configure and verify hardware-level user presence policies (Touch/PIN/Biometric).
-   **Storage Audit:** A utility to view the expanded capacity (100 FIDO2 resident keys and 64 OATH credentials).
-->

---

## 🛠 Prerequisites

-   **Android Studio**.
-   **Physical YubiKey** with 5.8.0 Beta firmware.
-   **NFC** or **USB** support on your test device.
-   **Min SDK:** 21 (Android 5.0).
-   **Target SDK:** 34 (Android 14).

---

## 🏗 Project Structure

The app is divided into feature-specific modules for easy copy-pasting into your own projects:

```text
android-demo/
├── app/src/main/java/com/yubico/eap/quickstart/track/
│   ├── arkg
│   │   ├── ARKGTrackView.kt /                 # UI Components (Compose/Views)
│   │   ├── ARKGTrackViewModel.kt /            # View Model connecting ARGK math and user
├── app/src/main/java/com/yubico/eap/quickstart/math
│   ├── Arkg.kt                                # Cryptography abstraction using bouncy castle
```

