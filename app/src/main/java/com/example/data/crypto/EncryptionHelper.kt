package com.example.data.crypto

import android.content.Context
import android.util.Log
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {
    private const val TAG = "EncryptionHelper"
    private const val PREFS_NAME = "e2ee_keys"
    private const val ALGORITHM_EC = "EC"
    private const val ALGORITHM_AES_GCM = "AES/GCM/NoPadding"
    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128 // bits

    // Hex helpers
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }

    // Get or generate our own EC KeyPair
    fun getOrCreateKeyPair(context: Context, myUid: String): KeyPair {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val privKeyHex = prefs.getString("my_priv_key_$myUid", null)
        val pubKeyHex = prefs.getString("my_pub_key_$myUid", null)

        if (privKeyHex != null && pubKeyHex != null) {
            try {
                val keyFactory = KeyFactory.getInstance(ALGORITHM_EC)
                val privSpec = PKCS8EncodedKeySpec(hexToBytes(privKeyHex))
                val pubSpec = X509EncodedKeySpec(hexToBytes(pubKeyHex))

                val privateKey = keyFactory.generatePrivate(privSpec)
                val publicKey = keyFactory.generatePublic(pubSpec)

                return KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reconstruct keypair, generating a new one", e)
            }
        }

        // Generate new keypair
        try {
            val kpg = KeyPairGenerator.getInstance(ALGORITHM_EC)
            kpg.initialize(256) // secp256r1
            val kp = kpg.generateKeyPair()

            prefs.edit()
                .putString("my_priv_key_$myUid", bytesToHex(kp.private.encoded))
                .putString("my_pub_key_$myUid", bytesToHex(kp.public.encoded))
                .apply()

            return kp
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate EC keypair", e)
        }
    }

    // Save remote contact's public key
    fun saveRemotePublicKey(context: Context, remoteUid: String, pubKeyHex: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("remote_pub_key_$remoteUid", pubKeyHex)
            .apply()
    }

    // Get remote contact's public key
    fun getRemotePublicKey(context: Context, remoteUid: String): PublicKey? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pubKeyHex = prefs.getString("remote_pub_key_$remoteUid", null) ?: return null
        return try {
            val keyFactory = KeyFactory.getInstance(ALGORITHM_EC)
            val pubSpec = X509EncodedKeySpec(hexToBytes(pubKeyHex))
            keyFactory.generatePublic(pubSpec)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse remote public key for $remoteUid", e)
            null
        }
    }

    // Derive shared secret and create an AES key
    fun deriveSharedKey(privateKey: PrivateKey, remotePublicKey: PublicKey): SecretKeySpec {
        return try {
            val agreement = KeyAgreement.getInstance("ECDH")
            agreement.init(privateKey)
            agreement.doPhase(remotePublicKey, true)
            val sharedSecret = agreement.generateSecret()

            // Hash shared secret with SHA-256 to derive AES-256 key
            val md = MessageDigest.getInstance("SHA-256")
            val aesKeyBytes = md.digest(sharedSecret)
            SecretKeySpec(aesKeyBytes, "AES")
        } catch (e: Exception) {
            throw RuntimeException("Failed to derive shared key", e)
        }
    }

    // Encrypt message
    fun encrypt(plaintext: String, secretKey: SecretKeySpec): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM_AES_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv // 12 bytes standard for GCM
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val ivHex = bytesToHex(iv)
            val cipherHex = bytesToHex(ciphertext)
            "$ivHex:$cipherHex"
        } catch (e: Exception) {
            Log.e(TAG, "Encryption error", e)
            plaintext // fallback
        }
    }

    // Decrypt message
    fun decrypt(encryptedPayload: String, secretKey: SecretKeySpec): String {
        return try {
            val parts = encryptedPayload.split(":")
            if (parts.size != 2) return encryptedPayload // not formatted properly, return as-is

            val iv = hexToBytes(parts[0])
            val ciphertext = hexToBytes(parts[1])

            val cipher = Cipher.getInstance(ALGORITHM_AES_GCM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption error", e)
            "🔒 Decryption Failed"
        }
    }
}
