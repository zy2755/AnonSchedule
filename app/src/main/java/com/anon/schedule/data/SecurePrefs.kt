package com.anon.schedule.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 少量敏感字符串（登录账号、密码、会话 token）的加密存储。
 *
 * 密钥由 Android Keystore 托管，不可导出；明文用 AES-GCM 加密后写进独立的
 * SharedPreferences 文件。不引 androidx.security:security-crypto 的原因：该库已被
 * 官方标记弃用，而这里要保护的只是几个短字符串，自带实现更轻，也不受库弃用影响。
 *
 * 安全默认值：Keystore 不可用或解密失败时，[getString] 返回 null、[putString] 返回
 * false，调用方表现为「未登录 / 需要重新登录」，不会退回明文落盘。
 */
internal object SecurePrefs {

    private const val FILE = "secure_prefs"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "anon_schedule_secret_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val SEPARATOR = ":"

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getString(context: Context, key: String): String? {
        val raw = prefs(context).getString(key, null) ?: return null
        val plain = decrypt(raw)
        // 系统清掉/轮换过密钥后旧密文永远解不开：删掉这条，让业务走「未登录」分支
        if (plain == null) prefs(context).edit().remove(key).apply()
        return plain
    }

    /** @return 加密并落盘是否成功；失败时不会写入任何内容 */
    fun putString(context: Context, key: String, value: String): Boolean {
        val encrypted = encrypt(value) ?: return false
        return runCatching {
            prefs(context).edit().putString(key, encrypted).commit()
        }.getOrDefault(false)
    }

    fun remove(context: Context, key: String) {
        prefs(context).edit().remove(key).apply()
    }

    // ---------- 加解密 ----------

    /** 存法：`Base64(iv):Base64(密文+认证标签)` */
    private fun encrypt(plain: String): String? = runCatching {
        val key = secretKey() ?: return@runCatching null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR +
            Base64.encodeToString(body, Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(stored: String): String? = runCatching {
        val parts = stored.split(SEPARATOR, limit = 2)
        if (parts.size != 2) return@runCatching null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        if (iv.size != IV_BYTES) return@runCatching null
        val key = secretKey() ?: return@runCatching null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey? {
        cachedKey?.let { return it }
        return runCatching {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generateKey()
        }.getOrNull()?.also { cachedKey = it }
    }

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
}
