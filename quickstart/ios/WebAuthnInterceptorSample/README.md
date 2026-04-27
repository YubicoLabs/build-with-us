# WebAuthnInterceptorSample

WebAuthnInterceptorSample is native iOS Swift app with an embedded WKWebView that bypasses/intercepts WebKit's built-in CTAP/WebAuthn implementation and uses the YubiKit Swift SDK as the CTAP client, giving you full control over the authentication flow, PIN UI, and access to extensions like PRF, signing.

## Overview

This sample app shows how to:
- Intercept standard WebAuthn `navigator.credentials.create()` and `navigator.credentials.get()` in a WKWebView
- Route WebAuthn requests to a YubiKey via NFC(iOS), USB-C, or Lightning NFC (iOS)
- Handle PIN entry and verification
- Support PRF extension (hmac-secret) for deriving secrets from credentials
- Support preview signing (v4) for YubiKey 5.8

YubiKey Connection Manager: This application has been updated to support YubiKey connections. For example, if there's not a connected YubiKey (USB-C/Lightning) during a request, the NFC dialog appears. If a USB-C/Lightning YubiKey is connected, NFC will close and a new wired connection is made.

## Build and Run

Open `WebAuthnInterceptorSample.xcodeproj` in Xcode and run on a physical device (iOS) or macOS. 

## Usage

1. Navigate to a WebAuthn-enabled site (defaults to https://demo.yubico.com)
2. Register or authenticate with your YubiKey
3. Enter PIN when prompted
4. Tap (NFC) or insert (USB-C/Lightning) your YubiKey
