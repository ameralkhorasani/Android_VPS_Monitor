package io.github.ameralkhorasani.outpost.data.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "ssh_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val PREFS_FILE = "outpost_secure_prefs"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    init {
        getOrCreateMasterKey()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    /**
     * Encrypts a PEM-formatted OpenSSH private key string using AES-256-GCM.
     * Returns Base64 encoded payload: IV + Ciphertext.
     */
    fun encryptPrivateKey(pemString: String): String {
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(pemString.toByteArray(Charsets.UTF_8))
        
        // Combine IV (12 bytes) and ciphertext
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64 encoded payload (IV + Ciphertext) back to PEM private key string.
     */
    fun decryptPrivateKey(ciphertextBase64: String): String {
        val combined = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        
        val ivSize = 12 // GCM default IV size
        val iv = ByteArray(ivSize)
        val encryptedBytes = ByteArray(combined.size - ivSize)
        
        System.arraycopy(combined, 0, iv, 0, ivSize)
        System.arraycopy(combined, ivSize, encryptedBytes, 0, encryptedBytes.size)
        
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Securely stores an encrypted private key in EncryptedSharedPreferences.
     */
    fun saveEncryptedKey(serverId: String, encryptedPrivateKey: String) {
        encryptedPrefs.edit().putString("server_key_$serverId", encryptedPrivateKey).apply()
    }

    /**
     * Retrieves the encrypted key from EncryptedSharedPreferences.
     */
    fun getEncryptedKey(serverId: String): String? {
        return encryptedPrefs.getString("server_key_$serverId", null)
    }

    /**
     * Removes the encrypted key from EncryptedSharedPreferences.
     */
    fun removeEncryptedKey(serverId: String) {
        encryptedPrefs.edit().remove("server_key_$serverId").apply()
    }
}
