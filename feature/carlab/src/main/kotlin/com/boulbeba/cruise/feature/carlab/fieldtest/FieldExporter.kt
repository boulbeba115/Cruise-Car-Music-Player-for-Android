package com.boulbeba.cruise.feature.carlab.fieldtest

import com.boulbeba.cruise.core.model.StorageRoots

import android.content.Context
import java.io.File

/** Copies the field log and every result file to the best output folder (USB stick first). */
object FieldExporter {

    data class Result(val primary: File?, val copies: List<File>, val error: String?)

    @Synchronized
    fun exportAll(context: Context): Result {
        val source = FieldLog.folder ?: return Result(null, emptyList(), "log not initialised")
        val targets = StorageRoots.outputDirs(context)
        if (targets.isEmpty()) return Result(null, emptyList(), "no writable storage found")
        val written = targets.mapNotNull { target ->
            runCatching {
                source.walkTopDown().filter { it.isFile }.forEach { file ->
                    val dest = File(target, file.relativeTo(source).path)
                    dest.parentFile?.mkdirs()
                    file.copyTo(dest, overwrite = true)
                }
                target
            }.getOrNull()
        }
        return Result(written.firstOrNull(), written, if (written.isEmpty()) "copy failed" else null)
    }
}
