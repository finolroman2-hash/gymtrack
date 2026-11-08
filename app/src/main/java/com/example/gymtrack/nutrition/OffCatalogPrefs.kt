package com.example.gymtrack.nutrition

import android.content.Context

object OffCatalogPrefs {
    const val PREFS_NAME = "gymtrack_prefs"
    const val KEY_CACHE_USED = "off_cache_used_foods"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CACHE_USED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CACHE_USED, enabled)
            .apply()
    }
}
