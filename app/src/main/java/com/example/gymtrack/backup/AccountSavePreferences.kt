package com.example.gymtrack.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.SharedPreferences

object AccountSavePreferences {

    private fun uriKey(userName: String) = "account_save_uri::$userName"

    fun getSavedUri(prefs: SharedPreferences, userName: String): Uri? =
        prefs.getString(uriKey(userName), null)?.let(Uri::parse)

    fun hasSavedOnce(prefs: SharedPreferences, userName: String): Boolean =
        getSavedUri(prefs, userName) != null

    fun rememberSaveLocation(context: Context, prefs: SharedPreferences, userName: String, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.edit().putString(uriKey(userName), uri.toString()).apply()
    }

    fun clearSavedLocation(prefs: SharedPreferences, userName: String) {
        prefs.edit().remove(uriKey(userName)).apply()
    }
}
