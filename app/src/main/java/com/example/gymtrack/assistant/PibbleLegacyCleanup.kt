package com.example.gymtrack.assistant

import android.content.Context
import java.io.File

/** Borra modelos GGUF viejos copiados al almacenamiento interno (liberan cientos de MB). */
fun deleteLegacyOnDeviceModels(context: Context) {
    val dir = context.applicationContext.filesDir ?: return
    dir.listFiles()?.forEach { file ->
        if (file.isFile && file.name.endsWith(".gguf", ignoreCase = true)) {
            runCatching { file.delete() }
        }
    }
    File(dir, "models").takeIf { it.isDirectory }?.listFiles()?.forEach { file ->
        if (file.isFile && file.name.endsWith(".gguf", ignoreCase = true)) {
            runCatching { file.delete() }
        }
    }
}
