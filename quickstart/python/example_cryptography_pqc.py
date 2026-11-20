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

from importlib.metadata import version

if int(version("cryptography").split(".")[0]) < 49:
    print("ML-DSA certificates require cryptography 49 or newer")
    exit(-1)

from cryptography.hazmat.primitives.asymmetric import (
    ed448,
    ed25519,
    mldsa,
    rsa,
    dsa,
    x448,
    x25519,
)

print("Welcome to private key generation using cryptography")

message = "hello world"
print(f'Signing message: "{message}"\n')

generators = {
    "ED448": ed448.Ed448PrivateKey.generate,
    "ED25519": ed25519.Ed25519PrivateKey.generate,
    "MLDSA44": mldsa.MLDSA44PrivateKey.generate,
    "MLDSA65": mldsa.MLDSA65PrivateKey.generate,
    "MLDSA87": mldsa.MLDSA87PrivateKey.generate,
}

for name in generators:
    generator = generators[name]
    print(name)

    key = generator()
    signature = key.sign(message.encode())
    print(f"singature length: {len(signature)}")

    publickey = key.public_key()

    try:
        print("expected succes: ", end="")
        verified = publickey.verify(signature=signature, data=message.encode())
        print("\033[32msuccess\033[m")
    except:
        print("\033[31mfailure\033[m")

    try:
        k2 = generator()
        pk2 = k2.public_key()

        print("expected failure: ", end="")
        verified = pk2.verify(signature=signature, data=message.encode())
        print("\033[32msuccess\033[m")
    except:
        print("\033[31mfailure\033[m")

    print()

print("done")
