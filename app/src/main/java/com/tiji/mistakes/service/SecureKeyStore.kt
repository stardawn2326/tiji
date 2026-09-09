package com.tiji.mistakes.service

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureKeyStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_ai", Context.MODE_PRIVATE)
    private val keyAlias = "tiji_ai_key"

    fun save(value: String, profileId: String = DEFAULT_PROFILE_ID) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
        preferences.edit().putString(keyFor(profileId), encoded).apply()
    }

    fun read(profileId: String = DEFAULT_PROFILE_ID): String {
        val encoded = preferences.getString(keyFor(profileId), null)
            ?: preferences.getString(LEGACY_KEY, null)
            ?: return ""
        return runCatching {
            val raw = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            }
            String(cipher.doFinal(raw.copyOfRange(12, raw.size)), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    fun clear(profileId: String = DEFAULT_PROFILE_ID) { preferences.edit().remove(keyFor(profileId)).apply() }

    private fun keyFor(profileId: String): String = "$KEY_PREFIX${profileId.ifBlank { DEFAULT_PROFILE_ID }}"

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private companion object {
        const val KEY_PREFIX = "api_key_"
        const val LEGACY_KEY = "api_key"
        const val DEFAULT_PROFILE_ID = "default"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
