package com.yubico.eap.quickstart.helpers

data class CoseAlgorithm(
    val name: String,
    val value: Int,
    val description: String = "",
    val capabilities: String = "",
    val changeController: String = "",
    val reference: List<String> = listOf(),
    val recommended: Boolean = true
)

fun findAlgorithm(value: Int): CoseAlgorithm? = allCoseAlgorithms.firstOrNull { it.value == value }

// @formatter:off
val allCoseAlgorithms: List<CoseAlgorithm> = listOf(
    // --- AEAD Algorithms ---
    CoseAlgorithm("A128GCM", 1, "AES-GCM mode w/ 128-bit key", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("A192GCM", 2, "AES-GCM mode w/ 192-bit key", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("A256GCM", 3, "AES-GCM mode w/ 256-bit key", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ChaCha20/Poly1305", 24, "ChaCha20/Poly1305 w/ 256-bit key, 96-bit nonce", "[kty]", "IETF", listOf("RFC9053"), true),

    // --- AES-CCM ---
    CoseAlgorithm("AES-CCM-16-64-128", 10, "AES-CCM mode 128-bit key, 64-bit tag, 13-byte nonce", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("AES-CCM-16-64-256", 11, "AES-CCM mode 256-bit key, 64-bit tag, 13-byte nonce", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("AES-CCM-64-64-128", 12, "AES-CCM mode 128-bit key, 64-bit tag, 7-byte nonce", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("AES-CCM-64-64-256", 13, "AES-CCM mode 256-bit key, 64-bit tag, 7-byte nonce", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("AES-CCM-16-128-128", 30, "AES-CCM mode 128-bit key, 128-bit tag, 13-byte nonce", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("AES-CCM-16-128-256", 31, "AES-CCM mode 256-bit key, 128-bit tag, 13-byte nonce", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("AES-CCM-64-128-128", 32, "AES-CCM mode 128-bit key, 128-bit tag, 7-byte nonce", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("AES-CCM-64-128-256", 33, "AES-CCM mode 256-bit key, 128-bit tag, 7-byte nonce", "[kty]", "IETF", listOf("RFC9053"), true),

    // --- MAC Algorithms ---
    CoseAlgorithm("HMAC 256/64", 4, "HMAC w/ SHA-256 truncated to 64 bits", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("HMAC 256/256", 5, "HMAC w/ SHA-256", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("HMAC 384/384", 6, "HMAC w/ SHA-384", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("HMAC 512/512", 7, "HMAC w/ SHA-512", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("AES-MAC 128/64", 14, "AES-MAC 128-bit key, 64-bit tag", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("AES-MAC 256/64", 15, "AES-MAC 256-bit key, 64-bit tag", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("AES-MAC 128/128", 25, "AES-MAC 128-bit key, 128-bit tag", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("AES-MAC 256/128", 26, "AES-MAC 256-bit key, 128-bit tag", "[kty]", "IETF", listOf("RFC9053"), true),

    // --- Signature Algorithms ---
    CoseAlgorithm("ES256", -7, "ECDSA using P-256 curve and SHA-256", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("EdDSA", -8, "EdDSA (Ed25519)", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ES384", -35, "ECDSA using P-384 curve and SHA-384", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ES512", -36, "ECDSA using P-521 curve and SHA-512", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("PS256", -37, "RSASSA-PSS w/ SHA-256", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("PS384", -38, "RSASSA-PSS w/ SHA-384", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("PS512", -39, "RSASSA-PSS w/ SHA-512", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ES256K", -47, "ECDSA using secp256k1 curve and SHA-256", "[kty]", "IETF", listOf("RFC8812", "RFC9053"), true),
    CoseAlgorithm("Ed448", -53, "EdDSA using the Ed448 parameter set", "[kty]", "IETF", listOf("RFC9864"), true),

    // Legacy RSA Signatures (Not Recommended)
    CoseAlgorithm("RS256", -257, "RSASSA-PKCS1-v1_5 using SHA-256", "[kty]", "IESG", listOf("RFC8812", "RFC9053"), false),
    CoseAlgorithm("RS384", -258, "RSASSA-PKCS1-v1_5 using SHA-384", "[kty]", "IESG", listOf("RFC8812", "RFC9053"), false),
    CoseAlgorithm("RS512", -259, "RSASSA-PKCS1-v1_5 using SHA-512", "[kty]", "IESG", listOf("RFC8812", "RFC9053"), false),

    // --- Post-Quantum Signatures (ML-DSA) ---
    CoseAlgorithm("ML-DSA-44", -48, "CBOR Object Signing Algorithm for ML-DSA-44", "[kty]", "IETF", listOf("RFC9964"), true),
    CoseAlgorithm("ML-DSA-65", -49, "CBOR Object Signing Algorithm for ML-DSA-65", "[kty]", "IETF", listOf("RFC9964"), true),
    CoseAlgorithm("ML-DSA-87", -50, "CBOR Object Signing Algorithm for ML-DSA-87", "[kty]", "IETF", listOf("RFC9964"), true),

    // --- Key Wrap Algorithms ---
    CoseAlgorithm("A128KW", -3, "AES Key Wrap w/ 128-bit key", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("A192KW", -4, "AES Key Wrap w/ 192-bit key", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("A256KW", -5, "AES Key Wrap w/ 256-bit key", "[kty]", "IETF", listOf("RFC9053"), true),

    // --- Key Derivation / Direct Agreement ---
    CoseAlgorithm("direct", -6, "Direct use of CEK", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("direct+HKDF-SHA-256", -10, "Direct CEK w/ HKDF-SHA-256", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("direct+HKDF-SHA-512", -11, "Direct CEK w/ HKDF-SHA-512", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("direct+HKDF-AES-128", -12, "Direct CEK w/ HKDF-AES-128", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("direct+HKDF-AES-256", -13, "Direct CEK w/ HKDF-AES-256", "[kty]", "IETF", listOf("RFC9053"), true),

    // --- ECDH (Ephemeral-Static / Static-Static) ---
    CoseAlgorithm("ECDH-ES+HKDF-256", -25, "ECDH ES w/ HKDF-256", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ECDH-ES+HKDF-512", -26, "ECDH ES w/ HKDF-512", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ECDH-SS+HKDF-256", -27, "ECDH SS w/ HKDF-256", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ECDH-SS+HKDF-512", -28, "ECDH SS w/ HKDF-512", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ECDH-ES+A128KW", -29, "ECDH ES w/ AES-KW 128", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ECDH-ES+A192KW", -30, "ECDH ES w/ AES-KW 192", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ECDH-ES+A256KW", -31, "ECDH ES w/ AES-KW 256", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ECDH-SS+A128KW", -32, "ECDH SS w/ AES-KW 128", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ECDH-SS+A192KW", -33, "ECDH SS w/ AES-KW 192", "[kty]", "IETF", listOf("RFC9053"), true),
    CoseAlgorithm("ECDH-SS+A256KW", -34, "ECDH SS w/ AES-KW 256", "[kty]", "IETF", listOf("RFC9053"), true),

    // --- Hashes ---
    CoseAlgorithm("SHA-1", -14, "SHA-1 Hash", "[kty]", "IETF", listOf("RFC9054"), false), // Not recommended
    CoseAlgorithm("SHA-256", -16, "SHA-256 Hash", "[kty]", "IETF", listOf("RFC9054"), true),
    CoseAlgorithm("SHA-384", -43, "SHA-384 Hash", "[kty]", "IETF", listOf("RFC9054"), true),
    CoseAlgorithm("SHA-512", -44, "SHA-512 Hash", "[kty]", "IETF", listOf("RFC9054"), true)
)
// @formatter:on
