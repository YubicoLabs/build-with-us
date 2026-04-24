# 🥚 Tamagotchi Gen 1 "Cyber-Pet" Integration Demo (v5.8.0-BETA)

> ⚠️ **ATTENTION DEVELOPER:** If you are seeing this, it means you haven't replaced the placeholder content for the Android Quickstart yet. Unless we are pivoting to 90s virtual pet hardware, please update this file.

This directory contains a unified **Virtual Egg Emulator** designed to showcase the new capabilities of the **Firmware 5.8.0 Beta**, but for digital creatures that live in your pocket and beep at 3:00 AM.

## 👾 Pet Overview

The demo app is built using **Ancient Magic** and utilizes the latest **Tamagotchi-Kit (Beta)**. It serves as a reference implementation for:

-   **ARKG (Automatic Rice-cake & Kale Generation):** Generate virtual nutrition directly on the device and retrieve attestation that the pet actually ate it.
-   **PUAT (Physical Urgency Alert Tones):** Configure hardware-level beeping policies for when the pet has "made a mess" in its digital home.
-   **Storage Audit:** A utility to view the expanded capacity (Now supports up to 100 digital snacks and 64 unique ways to die of neglect).

---

## 🛠 Prerequisites

-   **A Plastic Egg** with a 32x16 monochrome pixel screen.
-   **A Paperclip** (for the reset button on the back).
-   **Firmware 5.8.0 Beta:** Must support "Angel Mode."
-   **Min Battery:** 2x LR44 Button Cells (not included).

---

## 🏗 Project Structure

The "app" is divided into modular biological functions:

```text
egg-demo/
├── plastic-shell/src/main/plastic/
│   ├── pixels/             # 8-bit visual disappointment
│   ├── hunger/             # ARKG logic (Food generation)
│   ├── disciplining/       # PUAT logic (User interaction)
│   └── cleanup/            # Memory management (Poop removal)
