package com.boulbeba.cruise.feature.settings.general

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.data.db.LibraryDao
import com.boulbeba.cruise.core.data.library.LibraryScanner
import com.boulbeba.cruise.core.data.library.LibrarySettings
import com.boulbeba.cruise.core.playback.PlaybackSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GeneralState(
    val autoScan: Boolean,
    val minDurationMs: Long,
    val sleepPauseDelayMs: Long,
    val resumeOnWake: Boolean,
    val resumeOnBoot: Boolean,
)

sealed interface BackupState {
    data object Idle : BackupState
    data object Working : BackupState
    data class Saved(val path: String) : BackupState
    data class Restored(val summary: Backup.Summary) : BackupState
    data class Failed(val message: String) : BackupState
}

@HiltViewModel
class GeneralViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibrarySettings,
    private val playback: PlaybackSettings,
    private val scanner: LibraryScanner,
    private val backup: Backup,
    dao: LibraryDao,
) : ViewModel() {
    private val _state = MutableStateFlow(read())
    val state = _state.asStateFlow()
    val scan = scanner.state
    val trackCount = dao.countAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val _backup = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState = _backup.asStateFlow()

    private fun read() = GeneralState(library.autoScan, library.minDurationMs, playback.sleepPauseDelayMs, playback.resumeOnWake, playback.resumeOnBoot)

    fun rescan() = scanner.scan()
    fun setAutoScan(on: Boolean) { library.autoScan = on; _state.value = read() }
    fun setMinDuration(ms: Long) { library.minDurationMs = ms; _state.value = read() }
    fun setSleepDelay(ms: Long) { playback.sleepPauseDelayMs = ms; _state.value = read() }
    fun setResumeOnWake(on: Boolean) { playback.resumeOnWake = on; _state.value = read() }
    fun setResumeOnBoot(on: Boolean) { playback.resumeOnBoot = on; _state.value = read() }

    fun exportBackup() = viewModelScope.launch {
        _backup.value = BackupState.Working
        _backup.value = runCatching { BackupState.Saved(backup.export().path) }.getOrElse { BackupState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun restoreBackup() = viewModelScope.launch {
        val file = backup.find() ?: run { _backup.value = BackupState.Failed(Backup.FILE_NAME); return@launch }
        _backup.value = BackupState.Working
        _backup.value = runCatching { BackupState.Restored(backup.restore(file)) }.getOrElse { BackupState.Failed(it.message ?: it.javaClass.simpleName) }
    }

    /** Preferences are read once at start-up: relaunch so the restored ones apply everywhere. */
    fun restart() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        exitProcess(0)
    }
}
