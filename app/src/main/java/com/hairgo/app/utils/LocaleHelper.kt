
package com.hairgo.app.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleHelper {

    private const val PREFS = "hairgo_locale"
    private const val KEY_CURRENT = "current_language"

    // HairGo now supports English only.
    fun codeForLabel(label: String?): String = "en"

    fun normalize(code: String?): String = "en"

    fun saveForEmail(context: Context, email: String, code: String) {
        prefs(context).edit()
            .putString(emailKey(email), "en")
            .putString(KEY_CURRENT, "en")
            .apply()
    }

    fun codeForEmail(context: Context, email: String): String = "en"

    fun currentCode(context: Context): String = "en"

    fun apply(context: Context, code: String): Boolean {
        prefs(context).edit()
            .putString(KEY_CURRENT, "en")
            .apply()

        val english = LocaleListCompat.forLanguageTags("en")

        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == "en") {
            return false
        }

        AppCompatDelegate.setApplicationLocales(english)
        return true
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun emailKey(email: String): String =
        email.trim().lowercase(java.util.Locale.US)
}
