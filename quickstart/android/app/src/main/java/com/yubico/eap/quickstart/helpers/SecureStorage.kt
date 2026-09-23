package com.yubico.eap.quickstart.helpers

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorage(
    val keyAlias: String = "secure_alias",
) {
    private val androidKeyStore = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"

    /**
     * Encrypts the byte array and saves to disk.
     *
     *
     */
    fun store(
        context: Context,
        fileName: String,
        data: ByteArray
    ) {
        val file = File(context.filesDir, fileName)

        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)

        FileOutputStream(file).use { outputStream ->
            outputStream.write(iv.size)
            outputStream.write(iv)
            outputStream.write(encryptedData)
            outputStream.flush()
        }
    }

    /**
     * Reads the IV and the cipher text from disk, then decrypts and returns the raw bytes.
     *
     * @return null if not present, null if not correct bytearray of stored secured data.
     */
    fun retrieve(
        context: Context,
        fileName: String,
    ): ByteArray? {
        val file = File(context.filesDir, fileName)

        if (!file.exists()) return ByteArray(0)

        return try {
            FileInputStream(file).use { inputStream ->
                val ivSize = inputStream.read()
                if (ivSize <= 0) return null

                val iv = ByteArray(ivSize)
                inputStream.read(iv)

                val encryptedData = inputStream.readBytes()

                val cipher = Cipher.getInstance(transformation)
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

                cipher.doFinal(encryptedData)
            }
        } catch (fnf: FileNotFoundException) {
            return null
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        keyStore.getKey(keyAlias, null)?.let {
            return it as SecretKey
        }

        // Key doesn't exist, generate it
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
