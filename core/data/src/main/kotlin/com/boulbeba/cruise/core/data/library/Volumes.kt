package com.boulbeba.cruise.core.data.library

import android.content.Context
import android.os.Environment
import com.boulbeba.cruise.core.model.StorageRoots
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The music volumes currently mounted. Library queries only show tracks on these, so unplugging the USB stick
 * hides its music instantly and plugging it back shows it again without a rescan (SPEC LIB-6).
 */
@Singleton
class Volumes @Inject constructor(@ApplicationContext private val context: Context) {

    private val _mounted = MutableStateFlow(compute())
    val mounted: StateFlow<List<String>> = _mounted.asStateFlow()

    fun refresh() {
        _mounted.value = compute()
    }

    fun roots(): List<File> = compute().map(::File)

    /** Volume root a file belongs to (longest matching mounted root). */
    fun volumeOf(path: String): String = compute().filter { path.startsWith("$it/") }.maxByOrNull { it.length }
        ?: Environment.getExternalStorageDirectory().path

    private fun compute(): List<String> =
        (StorageRoots.removableRoots(context) + Environment.getExternalStorageDirectory())
            .filter { it.canRead() }
            .map { it.path }
            .distinct()
}
