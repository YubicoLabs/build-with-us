# 🤖 Yubico 5.8.0 Python Integration Demo

This directory contains several Python scripts showcasing the new capabilities of the **Firmware 5.8.0 Beta**.

Set up a virtual environment and install the dependency:

```bash
python -m venv .venv

# Windows
.venv\Scripts\activate
# macOS / Linux
source .venv/bin/activate

pip install "fido2>=2.2.0"
```

Then run a script like this:

```bash
python example_arkg.py
```

> [!NOTE]
> On Windows, run these from an **administrator terminal**. Without it, the
> native Windows WebAuthn path is used, which does not return the previewSign
> extension output and the ARKG example will fail.

## 📜 Script Overview

Let us take a quick look at the scripts in this folder. These cover the same 5.8
features as the other platforms, so for a deeper, language-agnostic explanation of
each feature (when to use it, how it works, real-world use cases), see the linked
.NET quickstart README for that feature.

### [example_credentials.py](./example_credentials.py)

Example script for creating new credentials, base for following examples.

### [example_arkg.py](./example_arkg.py)

The script for signing arbitrary data without the FIDO payload. Use that script to create a public key and sign a string of data with it. Finally it'll also verify its signature.

> Learn more about previewSign and ARKG: [.NET previewSign README](../dotnet/preview-sign/README.md).

### [example_prf.py](./example_prf.py)

Use the PRF (HMAC) extension to return a random value cryptographically linked to the credential and a given value.

> Learn more about hmac-secret / PRF: [.NET hmac-secret-prf README](../dotnet/hmac-secret-prf/README.md).

### example_ppuat.py (TBD)

> [!NOTE]
> Useful implementation pending.

### [preview-sign-rp/](./preview-sign-rp/)

A standalone relying-party helper for previewSign. It performs the offline
ARKG key derivation and signature verification (the relying-party side) that
pair with the .NET, Android, or iOS clients. See its
[README](./preview-sign-rp/README.md) for the full flow.

## 🛠 Prerequisites

-   **Python 3.10 or later**.
-   **Physical YubiKey** with 5.8.0 Beta firmware.

---

