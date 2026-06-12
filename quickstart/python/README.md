# 🤖 Yubico 5.8.0 Python Integration Demo

This directory contains several Python scripts showcasing the new capabilities of the **Firmware 5.8.0 Beta**.

Run a script like this:

```bash
uv run example_arkg.py
```

## 📜 Script Overview

Let us take a quick look at the scripts in this folder:

### [example_credentials.py](./example_credentials.py)

Example script for creating new credentials, base for following examples.

### [example_arkg.py](./example_arkg.py)

The script for signing arbitrary data without the FIDO payload. Use that script to create a public key and sign a string of data with it. Finally it'll also verify its signature.

### [example_prf.py](./example_prf.py)

Use the PRF (HMAC) extension to return a random value cryptographically linked to the credential and a given value.

### [example_ppuat.py](./example_ppuat.py)

> [!NOTE]
> Useful Implementation pending.

## 🛠 Prerequisites

-   [**uv**](https://docs.astral.sh/uv/).
-   **Python 3.14** as provided by uv.
-   **Physical YubiKey** with 5.8.0 Beta firmware.

---

