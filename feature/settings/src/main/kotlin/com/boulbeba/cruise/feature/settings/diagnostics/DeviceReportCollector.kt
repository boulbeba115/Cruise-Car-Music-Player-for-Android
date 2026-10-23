package com.boulbeba.cruise.feature.settings.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodecList
import android.media.audiofx.AudioEffect
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.InputDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Collects everything SPEC §10 Phase 0 needs to know about the unit. Read-only; no permission prompts. */
class DeviceReportCollector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun collect(uiInfo: List<Pair<String, String>>): DeviceReport = withContext(Dispatchers.IO) {
        DeviceReport(
            createdAtMillis = System.currentTimeMillis(),
            sections = listOf(
                section("Device") { device() },
                section("Display") { display() + uiInfo },
                section("CPU, memory, GPU") { cpuMemoryGpu() },
                section("Audio output") { audio() },
                section("Audio decoders") { decoders() },
                section("Storage volumes") { storage() },
                section("Input devices") { inputDevices() },
                section("Power, sensors, location") { powerSensors() },
                section("Remote access (adb / root / Wi-Fi)") { remoteAccess() },
                section("Vendor packages") { vendorPackages() },
                section("System properties") { systemProperties() },
            ),
        )
    }

    /** A failing probe must never hide the rest of the report. */
    private inline fun section(title: String, block: () -> List<Pair<String, String>>): ReportSection =
        ReportSection(title, runCatching(block).getOrElse { listOf("error" to it.toString()) })

    private fun device(): List<Pair<String, String>> = buildList {
        add("Manufacturer" to Build.MANUFACTURER)
        add("Brand" to Build.BRAND)
        add("Model" to Build.MODEL)
        add("Device" to Build.DEVICE)
        add("Product" to Build.PRODUCT)
        add("Board" to Build.BOARD)
        add("Hardware" to Build.HARDWARE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add("SoC" to "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        }
        add("ABIs" to Build.SUPPORTED_ABIS.joinToString())
        // Head-unit ROMs often fake the displayed version; the API level is what the system really runs.
        add("Real Android version" to "${androidNameFor(Build.VERSION.SDK_INT)} (API ${Build.VERSION.SDK_INT})")
        add("Displayed version" to Build.VERSION.RELEASE)
        add("Build" to Build.DISPLAY)
        add("Fingerprint" to Build.FINGERPRINT)
        add("Uptime" to "${SystemClock.elapsedRealtime() / 60_000} min")
    }

    @Suppress("DEPRECATION")
    private fun display(): List<Pair<String, String>> = buildList {
        val manager = context.getSystemService(DisplayManager::class.java)
        for (display in manager.displays) {
            val prefix = "Display ${display.displayId}"
            val real = DisplayMetrics().also { display.getRealMetrics(it) }
            add("$prefix name" to display.name)
            add("$prefix pixels" to "${real.widthPixels} x ${real.heightPixels}")
            add("$prefix density" to "${real.densityDpi} dpi (scale ${real.density}), xdpi ${real.xdpi}, ydpi ${real.ydpi}")
            add("$prefix size in dp" to "${(real.widthPixels / real.density).toInt()} x ${(real.heightPixels / real.density).toInt()}")
            add("$prefix refresh" to "${display.refreshRate} Hz")
            add("$prefix modes" to display.supportedModes.joinToString { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate}" })
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add("$prefix HDR" to (display.hdrCapabilities?.supportedHdrTypes?.joinToString() ?: "none"))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add("$prefix wide colour" to display.isWideColorGamut.toString())
            }
        }
        val config = context.resources.configuration
        add("Smallest width" to "${config.smallestScreenWidthDp} dp")
        add("Font scale" to config.fontScale.toString())
        add("Night mode" to ((config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES).toString())
    }

    private fun cpuMemoryGpu(): List<Pair<String, String>> = buildList {
        add("Cores" to Runtime.getRuntime().availableProcessors().toString())
        val cpuInfo = runCatching { File("/proc/cpuinfo").readLines() }.getOrDefault(emptyList())
        cpuInfo.filter { it.startsWith("Hardware") || it.startsWith("model name") || it.startsWith("CPU part") }
            .distinct()
            .forEach { line -> add(line.substringBefore(':').trim() to line.substringAfter(':').trim()) }
        val maxFreqs = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { cpu ->
            runCatching { File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong() / 1000 }.getOrNull()
        }
        if (maxFreqs.isNotEmpty()) add("Max frequency per core" to maxFreqs.joinToString { "$it MHz" })
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        add("RAM total" to "${memory.totalMem / 1_048_576} MB")
        add("RAM available" to "${memory.availMem / 1_048_576} MB")
        add("App heap" to "${activityManager.memoryClass} MB (large ${activityManager.largeMemoryClass} MB)")
        add("Low-RAM device" to activityManager.isLowRamDevice.toString())
        add("OpenGL ES (config)" to activityManager.deviceConfigurationInfo.glEsVersion)
        addAll(glStrings())
    }

    private fun glStrings(): List<Pair<String, String>> = runCatching {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        EGL14.eglChooseConfig(
            display,
            intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE),
            0, configs, 0, 1, count, 0,
        )
        val eglContext = EGL14.eglCreateContext(
            display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, surface, surface, eglContext)
        val result = listOf(
            "GPU renderer" to GLES20.glGetString(GLES20.GL_RENDERER).orEmpty(),
            "GPU vendor" to GLES20.glGetString(GLES20.GL_VENDOR).orEmpty(),
            "GL version" to GLES20.glGetString(GLES20.GL_VERSION).orEmpty(),
        )
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, eglContext)
        result
    }.getOrElse { listOf("GPU" to "unavailable: $it") }

    private fun audio(): List<Pair<String, String>> = buildList {
        val audioManager = context.getSystemService(AudioManager::class.java)
        add("Native output rate" to (audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE) ?: "?") + " Hz")
        add("Frames per buffer" to (audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) ?: "?"))
        @Suppress("DEPRECATION")
        add("AudioTrack native rate" to "${AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)} Hz")
        add("Music volume" to "${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)} / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}")
        add("Volume fixed" to audioManager.isVolumeFixed.toString())
        add("Low-latency feature" to context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY).toString())
        add("Pro audio feature" to context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO).toString())
        for (device in audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            val prefix = "Output #${device.id} ${deviceType(device.type)}"
            add(prefix to "\"${device.productName}\" rates=${device.sampleRates.joinToString().ifEmpty { "any" }} " +
                "channels=${device.channelCounts.joinToString().ifEmpty { "any" }} encodings=${device.encodings.joinToString { encodingName(it) }}")
        }
        for (rate in intArrayOf(44_100, 48_000, 96_000, 192_000)) {
            add("AudioTrack float @ $rate" to trackTest(rate, AudioFormat.ENCODING_PCM_FLOAT))
            add("AudioTrack 16-bit @ $rate" to trackTest(rate, AudioFormat.ENCODING_PCM_16BIT))
        }
        val effects = runCatching { AudioEffect.queryEffects()?.toList().orEmpty() }.getOrDefault(emptyList())
        add("System audio effects" to effects.joinToString { "${it.name} (${it.implementor})" }.ifEmpty { "none" })
    }

    private fun trackTest(rate: Int, encoding: Int): String = runCatching {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(rate)
            .setEncoding(encoding)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        // Deprecated on API 33+ in favour of AudioManager.getDirectPlaybackSupport; unavailable before API 29.
        @Suppress("DEPRECATION")
        val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AudioTrack.isDirectPlaybackSupported(format, attributes).toString()
        } else {
            "n/a"
        }
        val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, encoding)
        if (minBuffer <= 0) return "no (min buffer $minBuffer), direct=$direct"
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuffer)
            .build()
        val ok = track.state == AudioTrack.STATE_INITIALIZED
        track.release()
        "${if (ok) "ok" else "failed"}, min buffer $minBuffer B, direct=$direct"
    }.getOrElse { "error: ${it.message}" }

    private fun decoders(): List<Pair<String, String>> {
        val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }
        return codecs.mapNotNull { info ->
            val audioTypes = info.supportedTypes.filter { it.startsWith("audio/") }
            if (audioTypes.isEmpty()) return@mapNotNull null
            val details = audioTypes.joinToString { type ->
                val audio = runCatching { info.getCapabilitiesForType(type).audioCapabilities }.getOrNull()
                val rates = audio?.supportedSampleRateRanges?.joinToString { "${it.lower}-${it.upper}" } ?: "?"
                "$type [max ch ${audio?.maxInputChannelCount ?: "?"}, rates $rates]"
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                listOfNotNull(
                    "hw".takeIf { info.isHardwareAccelerated },
                    "vendor".takeIf { info.isVendor },
                    "alias".takeIf { info.isAlias },
                ).joinToString(",")
            } else {
                // Before API 29 only the name tells: Google's software codecs start with OMX.google. / c2.android.
                if (info.name.startsWith("OMX.google.") || info.name.startsWith("c2.android.")) "sw" else "vendor?"
            }
            "${info.name}${if (flags.isNotEmpty()) " ($flags)" else ""}" to details
        }
    }

    private fun storage(): List<Pair<String, String>> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            for (volume in storageManager.storageVolumes) {
                val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) volume.directory?.path ?: "-" else "-"
                add(
                    volume.getDescription(context) to
                        "path=$path state=${volume.state} uuid=${volume.uuid ?: "-"} " +
                        "removable=${volume.isRemovable} primary=${volume.isPrimary} emulated=${volume.isEmulated}",
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add("All files access granted" to runCatching { Environment.isExternalStorageManager().toString() }.getOrDefault("unknown"))
        }
        // Head units mount USB sticks in vendor-specific places (/storage/XXXX-XXXX, /mnt/usb_storage, …).
        for (dir in listOf("/storage", "/mnt", "/mnt/media_rw")) {
            add("ls $dir" to (File(dir).list()?.sorted()?.joinToString() ?: "not readable"))
        }
        val mountPattern = Regex("""\s(/storage|/mnt)/\S+""")
        runCatching { File("/proc/mounts").readLines() }.getOrDefault(emptyList())
            .filter { mountPattern.containsMatchIn(it) }
            .forEachIndexed { i, line -> add("mount $i" to line) }
    }

    private fun inputDevices(): List<Pair<String, String>> {
        val inputManager = context.getSystemService(InputManager::class.java)
        return inputManager.inputDeviceIds.toList().mapNotNull { id ->
            val device = inputManager.getInputDevice(id) ?: return@mapNotNull null
            val sources = buildList {
                if (device.supportsSource(InputDevice.SOURCE_KEYBOARD)) add("keyboard")
                if (device.supportsSource(InputDevice.SOURCE_DPAD)) add("dpad")
                if (device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)) add("touchscreen")
                if (device.supportsSource(InputDevice.SOURCE_MOUSE)) add("mouse")
                if (device.supportsSource(InputDevice.SOURCE_ROTARY_ENCODER)) add("rotary")
            }.joinToString(",")
            "#$id ${device.name}" to "vendor=0x%04x product=0x%04x sources=%s virtual=%s external=%s".format(
                device.vendorId, device.productId, sources, device.isVirtual,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) device.isExternal else "?",
            )
        }
    }

    private fun powerSensors(): List<Pair<String, String>> = buildList {
        val power = context.getSystemService(PowerManager::class.java)
        add("Battery optimisation ignored" to power.isIgnoringBatteryOptimizations(context.packageName).toString())
        val sensors = context.getSystemService(SensorManager::class.java)
        add("Light sensor" to (sensors.getDefaultSensor(Sensor.TYPE_LIGHT)?.name ?: "none"))
        add("Sensors" to sensors.getSensorList(Sensor.TYPE_ALL).joinToString { it.name }.ifEmpty { "none" })
        add("GPS feature" to context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS).toString())
        val location = context.getSystemService(LocationManager::class.java)
        add("Location providers" to location.allProviders.joinToString())
        add("Bluetooth feature" to context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH).toString())
        add("Automotive feature" to context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE).toString())
    }

    /**
     * Whether the PC could reach the unit directly (adb over Wi-Fi, root), which would replace most trips
     * to the car for installing and debugging.
     */
    private fun remoteAccess(): List<Pair<String, String>> = buildList {
        val resolver = context.contentResolver
        add("Developer options" to Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0).toString())
        add("USB debugging (adb)" to Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0).toString())
        val props = systemProperties().toMap()
        for (key in listOf("ro.debuggable", "ro.secure", "ro.adb.secure", "service.adb.tcp.port", "persist.adb.tcp.port", "persist.sys.usb.config", "sys.usb.config", "init.svc.adbd")) {
            add(key to (props[key] ?: "-"))
        }
        val suPaths = listOf("/system/xbin/su", "/system/bin/su", "/sbin/su", "/su/bin/su", "/system/app/Superuser.apk", "/data/adb/magisk")
        add("su binaries" to suPaths.filter { File(it).exists() }.joinToString().ifEmpty { "none found" })
        val addresses = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { nif ->
                nif.inetAddresses.toList().filter { it is java.net.Inet4Address }.map { "${nif.name}=${it.hostAddress}" }
            }
        }.getOrDefault(emptyList())
        add("IPv4 addresses" to addresses.joinToString().ifEmpty { "none (Wi-Fi off?)" })
    }

    private fun vendorPackages(): List<Pair<String, String>> {
        val keywords = listOf(
            "can", "mcu", "radio", "fyt", "syu", "microntek", "hct", "ts10", "ts18", "unisoc", "sprd",
            "allwinner", "rockchip", "steer", "swc", "carlink", "zlink", "launcher", "dsp", "audio",
            "eq", "music", "media", "bt", "bluetooth", "car", "auto", "renault",
        )
        val packages = context.packageManager.getInstalledPackages(0)
        val matches = packages.map { it.packageName }
            .filter { name -> keywords.any { name.contains(it, ignoreCase = true) } }
            .sorted()
        return listOf("Installed packages" to packages.size.toString()) +
            matches.map { name -> name to (context.packageManager.getLaunchIntentForPackage(name)?.let { "launchable" } ?: "") }
    }

    private fun systemProperties(): List<Pair<String, String>> {
        val output = runCatching {
            ProcessBuilder("getprop").redirectErrorStream(true).start().inputStream.bufferedReader().readLines()
        }.getOrDefault(emptyList())
        val pattern = Regex("""^\[(.*)]: \[(.*)]$""")
        return output.mapNotNull { line ->
            pattern.find(line)?.destructured?.let { (key, value) -> key to value }
        }
    }

    private fun androidNameFor(api: Int): String = when (api) {
        23 -> "Android 6.0"
        24 -> "Android 7.0"
        25 -> "Android 7.1"
        26 -> "Android 8.0"
        27 -> "Android 8.1"
        28 -> "Android 9"
        29 -> "Android 10"
        30 -> "Android 11"
        31 -> "Android 12"
        32 -> "Android 12L"
        33 -> "Android 13"
        34 -> "Android 14"
        35 -> "Android 15"
        36 -> "Android 16"
        else -> "Android (API $api)"
    }

    private fun deviceType(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "BUILTIN_EARPIECE"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "BUILTIN_SPEAKER_SAFE"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "LINE_ANALOG"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "LINE_DIGITAL"
        AudioDeviceInfo.TYPE_AUX_LINE -> "AUX_LINE"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_BUS -> "BUS"
        AudioDeviceInfo.TYPE_FM -> "FM"
        AudioDeviceInfo.TYPE_TELEPHONY -> "TELEPHONY"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "REMOTE_SUBMIX"
        AudioDeviceInfo.TYPE_IP -> "IP"
        AudioDeviceInfo.TYPE_DOCK -> "DOCK"
        AudioDeviceInfo.TYPE_HEARING_AID -> "HEARING_AID"
        else -> "TYPE_$type"
    }

    private fun encodingName(encoding: Int): String = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> "PCM16"
        AudioFormat.ENCODING_PCM_8BIT -> "PCM8"
        AudioFormat.ENCODING_PCM_FLOAT -> "FLOAT"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM24"
        AudioFormat.ENCODING_PCM_32BIT -> "PCM32"
        AudioFormat.ENCODING_AC3 -> "AC3"
        AudioFormat.ENCODING_E_AC3 -> "EAC3"
        AudioFormat.ENCODING_DTS -> "DTS"
        AudioFormat.ENCODING_MP3 -> "MP3"
        AudioFormat.ENCODING_AAC_LC -> "AAC"
        else -> "#$encoding"
    }
}
