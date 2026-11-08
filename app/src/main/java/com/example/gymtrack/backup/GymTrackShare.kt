package com.example.gymtrack.backup

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

sealed class ShareOffer {
    data class Macrocycle(val json: JSONObject, val name: String) : ShareOffer()
    data class Account(val json: JSONObject, val userName: String) : ShareOffer()
}

object GymTrackShare {

    const val KIND_MACROCYCLE = "macrocycle"
    const val KIND_ACCOUNT = "account"
    const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"

    fun peekKind(json: JSONObject): String? {
        val declared = json.optString("gymtrack_kind").ifBlank { null }
        if (declared == KIND_MACROCYCLE || declared == KIND_ACCOUNT) return declared
        if (json.has("macrocycle") && !json.has("exercises")) return KIND_MACROCYCLE
        if (json.has("backup_version") && json.has("user_name") && json.has("exercises")) {
            return KIND_ACCOUNT
        }
        return null
    }

    fun offerFromJson(json: JSONObject): ShareOffer? = when (peekKind(json)) {
        KIND_MACROCYCLE -> {
            val name = json.optJSONObject("macrocycle")?.optString("name").orEmpty()
                .ifBlank { "Macrociclo" }
            ShareOffer.Macrocycle(json, name)
        }
        KIND_ACCOUNT -> {
            val name = json.optString("user_name").ifBlank { "Cuenta" }
            ShareOffer.Account(json, name)
        }
        else -> null
    }

    fun parseRaw(raw: String?): ShareOffer? {
        if (raw.isNullOrBlank()) return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return offerFromJson(json)
    }

    suspend fun parseIntent(context: Context, intent: Intent?): ShareOffer? {
        if (intent == null) return null
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            else -> intent.data
        }
        val fromUri = uri?.let { readUri(context, it) }
        if (fromUri != null) return fromUri
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        return parseRaw(text)
    }

    private fun readUri(context: Context, uri: Uri): ShareOffer? {
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readText()
        }.getOrNull()
        return parseRaw(raw)
    }

    fun shareJson(context: Context, json: JSONObject, fileName: String, chooserTitle: String) {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(json.toString(2), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
            file,
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            clipData = ClipData.newRawUri(fileName, uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }
}
