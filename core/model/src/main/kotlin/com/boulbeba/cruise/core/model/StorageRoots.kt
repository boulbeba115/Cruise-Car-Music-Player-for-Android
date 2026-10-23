package com.boulbeba.cruise.core.model

import android.content.Context
import android.os.Environment
import java.io.File

/** Finds removable volumes (USB sticks, SD cards) the way budget head units expose them. */
object StorageRoots {

    private val vendorMountPoints = listOf(
        "/mnt/usb_storage", "/mnt/usb_storage1", "/mnt/usb_storage2", "/mnt/usbhost", "/mnt/usbhost1",
        "/mnt/udisk", "/mnt/udisk1", "/mnt/usb", "/storage/usbotg", "/storage/udisk", "/storage/usb0",
        "/storage/usb1", "/mnt/external_sd", "/mnt/extsd", "/storage/extsd",
    )

    /** Readable roots other than the built-in storage, largest first. */
    fun removableRoots(context: Context): List<File> {
        val primary = Environment.getExternalStorageDirectory().canonicalPath
        val candidates = buildList {
            File("/storage").listFiles()?.forEach { if (it.name != "emulated" && it.name != "self") add(it) }
            vendorMountPoints.forEach { add(File(it)) }
            File("/mnt/media_rw").listFiles()?.let { addAll(it) }
            // App-specific dirs exist on every volume Android knows; strip back to the volume root.
            context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
                val path = dir.absolutePath
                val idx = path.indexOf("/Android/data/")
                if (idx > 0) add(File(path.substring(0, idx)))
            }
        }
        return candidates
            .mapNotNull { runCatching { it.canonicalFile }.getOrNull() }
            .distinctBy { it.path }
            .filter { it.path != primary && !it.path.startsWith("$primary/") }
            .filter { it.isDirectory && it.canRead() && (it.list()?.isNotEmpty() == true) }
            .sortedByDescending { runCatching { it.totalSpace }.getOrDefault(0L) }
    }

    /**
     * Where to write the field-test output, in order of how easy it is to find on a PC:
     * the USB stick's root, the app's folder on the USB stick, then the built-in storage.
     */
    fun outputDirs(context: Context): List<File> {
        val onUsbRoot = removableRoots(context).map { File(it, "cruise-fieldtest") }
        val appDirsOnRemovable = context.getExternalFilesDirs(null).filterNotNull().drop(1)
        val internal = listOfNotNull(context.getExternalFilesDir(null))
        return (onUsbRoot + appDirsOnRemovable + internal).filter(::isWritable)
    }

    private fun isWritable(dir: File): Boolean = runCatching {
        dir.mkdirs()
        val probe = File(dir, ".cruise-write-test")
        probe.writeText("ok")
        probe.delete()
        true
    }.getOrDefault(false)
}
