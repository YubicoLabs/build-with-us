# Copyright (c) 2026 Yubico AB
# All rights reserved.
#
#   Redistribution and use in source and binary forms, with or
#   without modification, are permitted provided that the following
#   conditions are met:
#
#    1. Redistributions of source code must retain the above copyright
#       notice, this list of conditions and the following disclaimer.
#    2. Redistributions in binary form must reproduce the above
#       copyright notice, this list of conditions and the following
#       disclaimer in the documentation and/or other materials provided
#       with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
# FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
# INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
# BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
# LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
# ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.

"""
Reads the authenticatorGetInfo response and prints the FIDO2 getInfo fields
added in firmware 5.8. These let a platform guide the user (PIN length and
complexity, biometric-use tracking) and tailor reset and attestation flows.

No PIN and no touch are required, so this is a good first script to run.
"""

from fido2.ctap2 import Ctap2
from fido2.hid import CtapHidDevice

try:
    from fido2.pcsc import CtapPcscDevice
except ImportError:
    CtapPcscDevice = None


def enumerate_devices():
    for dev in CtapHidDevice.list_devices():
        yield dev
    if CtapPcscDevice:
        for dev in CtapPcscDevice.list_devices():
            yield dev


def main():
    dev = next(enumerate_devices(), None)
    if dev is None:
        print("No FIDO authenticator found. Plug in your YubiKey and try again.")
        return

    info = Ctap2(dev).get_info()

    print("Firmware getInfo (5.8 fields)")
    print("-" * 50)

    # PIN-related fields: let the platform guide the user toward a PIN the
    # authenticator will accept, and prompt for the PIN again periodically.
    print(f"  maxPINLength:             {info.max_pin_length}")
    print(f"  pinComplexityPolicy:      {info.pin_complexity_policy}")
    if info.pin_complexity_policy_url:
        print(f"  pinComplexityPolicyURL:   {info.pin_complexity_policy_url.decode('utf-8', 'replace')}")
    print(f"  uvCountSinceLastPinEntry: {info.uv_count_since_pin}")

    # Attestation: which attestation formats the key supports (future-proofing
    # for new formats such as post-quantum).
    print(f"  attestationFormats:       {info.attestation_formats}")

    # Reset policy: which transports allow a reset, and whether a long touch
    # is required to confirm it.
    print(f"  transportsForReset:       {info.transports_for_reset}")
    print(f"  longTouchForReset:        {info.long_touch_for_reset}")

    print("-" * 50)
    print(f"  previewSign supported:    {'previewSign' in info.extensions}")

    dev.close()


if __name__ == "__main__":
    main()
