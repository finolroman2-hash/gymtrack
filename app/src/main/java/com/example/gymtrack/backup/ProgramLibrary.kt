package com.example.gymtrack.backup

import android.content.Context
import org.json.JSONObject
import java.io.File

data class SavedProgram(
    val fileName: String,
    val name: String,
    val savedAt: Long,
)

object ProgramLibrary {

    private const val DIR = "program_library"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { mkdirs() }

    fun list(context: Context): List<SavedProgram> {
        val files = dir(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty()
        return files.mapNotNull { file ->
            runCatching {
                val root = JSONObject(file.readText(Charsets.UTF_8))
                SavedProgram(
                    fileName = file.name,
                    name = root.optJSONObject("macrocycle")?.optString("name")
                        ?.ifBlank { null }
                        ?: file.nameWithoutExtension,
                    savedAt = root.optLong("exported_at", file.lastModified()),
                )
            }.getOrNull()
        }.sortedByDescending { it.savedAt }
    }

    fun save(context: Context, json: JSONObject, suggestedName: String): Boolean {
        return try {
            val safe = suggestedName.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_')
                .ifBlank { "macrociclo" }
            val fileName = "${System.currentTimeMillis()}_$safe.json"
            File(dir(context), fileName).writeText(json.toString(2), Charsets.UTF_8)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun read(context: Context, fileName: String): JSONObject? {
        val file = File(dir(context), fileName)
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun delete(context: Context, fileName: String): Boolean {
        val file = File(dir(context), fileName)
        return file.isFile && file.delete()
    }
}
