package com.boulbeba.cruise.feature.settings.fieldtest

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

/**
 * Copies the unit's audio / codec / key-layout configuration and its vendor system apps to the output
 * folder, so the CAN-bus key messages, ignition broadcasts and audio routing can be worked out on the PC
 * instead of with more trips to the car. Read-only on the unit.
 */
object VendorExport {

    private const val MAX_APK_BYTES = 150L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 800L * 1024 * 1024

    private val configDirs = listOf("/system/etc", "/vendor/etc", "/odm/etc", "/product/etc")
    private val configPattern = Regex(
        "(audio_policy.*|audio_effects.*|.*mixer_paths.*|media_codecs.*|audio_hw.*|audio_para.*|.*codec.*\\.xml|" +
            "default_volume_tables.*|audio_platform_info.*|.*\\.kl|.*\\.kcm|.*\\.idc|build\\.prop)",
        RegexOption.IGNORE_CASE,
    )
    private val singleFiles = listOf(
        "/system/build.prop", "/vendor/build.prop", "/proc/cpuinfo", "/proc/meminfo", "/proc/version",
        "/proc/asound/cards", "/proc/asound/pcm", "/proc/asound/devices", "/proc/mounts", "/proc/bus/input/devices",
    )
    private val keyLayoutDirs = listOf("/system/usr/keylayout", "/vendor/usr/keylayout", "/system/usr/idc", "/vendor/usr/idc")
    /**
     * Only big Google apps are skipped: head-unit vendors often ship modified AOSP apps (com.android.settings
     * with steering-wheel / factory pages, com.android.launcher3 with CAN hooks), so those are kept.
     */
    private val skippedPrefixes = listOf(
        "com.google.", "com.android.chrome", "com.android.vending", "com.android.webview", "org.chromium.",
    )

    fun export(context: Context, target: File, onProgress: (String) -> Unit): String {
        val out = File(target, "vendor").apply { mkdirs() }
        val log = StringBuilder()

        onProgress("Copying configuration files…")
        val configOut = File(out, "config").apply { mkdirs() }
        var configCount = 0
        val candidates = singleFiles.map(::File) +
            configDirs.flatMap { dir -> File(dir).walkTopDown().maxDepth(3).filter { it.isFile && configPattern.matches(it.name) }.toList() } +
            keyLayoutDirs.flatMap { dir -> File(dir).listFiles()?.toList().orEmpty() }
        for (file in candidates.distinctBy { it.path }) {
            if (!file.canRead()) continue
            val dest = File(configOut, file.path.trimStart('/').replace('/', '_'))
            if (runCatching { dest.writeBytes(file.readBytes()) }.isSuccess) configCount++
        }
        log.appendLine("Configuration files copied: $configCount")

        onProgress("Copying vendor apps…")
        val apkOut = File(out, "apks").apply { mkdirs() }
        var total = 0L
        val pm = context.packageManager
        val vendorApps = pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .filter { app -> app.packageName != "android" && skippedPrefixes.none { app.packageName.startsWith(it) } }
            // Smallest first: the CAN / MCU / key services are small and must fit within the budget.
            .sortedBy { File(it.sourceDir).length() }
        for (app in vendorApps) {
            val sources = listOf(app.sourceDir) + app.splitSourceDirs.orEmpty()
            for ((i, src) in sources.withIndex()) {
                val file = File(src)
                val size = file.length()
                val name = if (i == 0) "${app.packageName}.apk" else "${app.packageName}.split$i.apk"
                when {
                    !file.canRead() -> log.appendLine("  unreadable  $name")
                    size > MAX_APK_BYTES -> log.appendLine("  too large   $name (${size / 1_048_576} MB)")
                    total + size > MAX_TOTAL_BYTES -> log.appendLine("  over budget $name (${size / 1_048_576} MB)")
                    else -> runCatching { file.copyTo(File(apkOut, name), overwrite = true) }
                        .onSuccess { total += size; log.appendLine("  copied      $name (${size / 1024} KB)  label=${pm.getApplicationLabel(app)}") }
                        .onFailure { log.appendLine("  failed      $name: ${it.message}") }
                }
            }
            onProgress("Vendor apps: ${total / 1_048_576} MB copied…")
        }
        log.appendLine("Vendor apps: ${vendorApps.size}, copied ${total / 1_048_576} MB")
        return log.toString()
    }
}
