package com.hairgo.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale

object BiometricCredentialStore {

    private const val PREFS = "hairgo_biometric"
    private const val KEY_EMAILS = "saved_emails"
    private const val LEGACY_EMAIL = "biometric_email"
    private const val LEGACY_PASSWORD = "biometric_password"
    private const val LEGACY_ENABLED = "biometric_enabled"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(context: Context, email: String, password: String) {
        val normalized = normalize(email)
        if (normalized.isEmpty() || password.isEmpty()) return

        try {
            val stored = prefs(context)
            migrateLegacy(stored)

            val emails = HashSet(stored.getStringSet(KEY_EMAILS, emptySet()) ?: emptySet())
            emails.add(normalized)

            stored.edit()
                .putStringSet(KEY_EMAILS, emails)
                .putString(passwordKey(normalized), password)
                .putBoolean(LEGACY_ENABLED, true)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun emails(context: Context): List<String> {
        return try {
            val stored = prefs(context)
            migrateLegacy(stored)
            stored.getStringSet(KEY_EMAILS, emptySet())
                ?.map { normalize(it) }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                ?.sorted()
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun password(context: Context, email: String): String? {
        val normalized = normalize(email)
        if (normalized.isEmpty()) return null

        return try {
            val stored = prefs(context)
            migrateLegacy(stored)
            stored.getString(passwordKey(normalized), null)
        } catch (e: Exception) {
            null
        }
    }

    fun hasLinkedAccount(context: Context): Boolean {
        return emails(context).isNotEmpty()
    }

    fun clear(context: Context) {
        try {
            prefs(context).edit().clear().apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun migrateLegacy(stored: SharedPreferences) {
        if (!stored.getBoolean(LEGACY_ENABLED, false)) return

        val legacyEmail = normalize(stored.getString(LEGACY_EMAIL, null))
        val legacyPassword = stored.getString(LEGACY_PASSWORD, null)
        if (legacyEmail.isEmpty() || legacyPassword.isNullOrEmpty()) return

        val emails = HashSet(stored.getStringSet(KEY_EMAILS, emptySet()) ?: emptySet())
        val alreadyMoved = emails.contains(legacyEmail) &&
                !stored.getString(passwordKey(legacyEmail), null).isNullOrEmpty()
        if (alreadyMoved) {
            stored.edit()
                .remove(LEGACY_EMAIL)
                .remove(LEGACY_PASSWORD)
                .apply()
            return
        }

        emails.add(legacyEmail)
        stored.edit()
            .putStringSet(KEY_EMAILS, emails)
            .putString(passwordKey(legacyEmail), legacyPassword)
            .remove(LEGACY_EMAIL)
            .remove(LEGACY_PASSWORD)
            .apply()
    }

    private fun normalize(email: String?): String {
        return email?.trim()?.lowercase(Locale.US) ?: ""
    }

    private fun passwordKey(email: String): String {
        return "biometric_password_$email"
    }
}