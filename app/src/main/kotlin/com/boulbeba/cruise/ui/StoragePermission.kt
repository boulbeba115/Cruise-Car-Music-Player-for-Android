package com.boulbeba.cruise.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.component.CruiseIconButton
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.boulbeba.cruise.R
import com.boulbeba.cruise.core.ui.StorageAccess

/** Whether Cruise can read the music: the storage permission up to Android 10, "All files access" from Android 11. */
fun hasStorageAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
} else {
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

/**
 * Asks for storage access as soon as the app opens (CHANGE_REQUESTS.md CR-03) and again whenever a screen calls
 * [StorageAccess.request]. Keeps [StorageAccess.granted] current; [onGranted] runs when access arrives.
 */
@Composable
fun StoragePermissionEffect(onGranted: () -> Unit) {
    val context = LocalContext.current
    var askedAtStart by rememberSaveable { mutableStateOf(false) }
    val refresh = {
        val now = hasStorageAccess(context)
        if (now && !StorageAccess.granted.value) onGranted()
        StorageAccess.granted.value = now
    }
    val afterAsking = {
        refresh()
        if (!StorageAccess.granted.value) Toast.makeText(context, R.string.access_refused, Toast.LENGTH_LONG).show()
    }
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { afterAsking() }
    val settingsPage = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { afterAsking() }
    val ask = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            runCatching { settingsPage.launch(intent) }
                .recoverCatching { settingsPage.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else {
            // Read and write are one group: a single dialog. Write is needed for backups and playlist exports.
            runtime.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
        Unit
    }
    LaunchedEffect(Unit) {
        // Start pessimistic only if access is really missing, so screens never flash the "allow" card.
        StorageAccess.granted.value = hasStorageAccess(context)
        if (!StorageAccess.granted.value && !askedAtStart) {
            askedAtStart = true
            ask()
        }
    }
    LaunchedEffect(Unit) {
        var seen = StorageAccess.asks.value
        StorageAccess.asks.collect { if (it != seen) { seen = it; ask() } }
    }
    // Back from the system settings page (Android 11+) or from the app info screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
}

/**
 * Shown over the player while storage access is missing (CHANGE_REQUESTS.md CR-12): the app stays usable, and one tap
 * asks again. Closing it hides it until the next start.
 */
@Composable
fun AccessBanner(modifier: Modifier = Modifier) {
    val granted by StorageAccess.granted.collectAsStateWithLifecycle()
    var closed by rememberSaveable { mutableStateOf(false) }
    AnimatedVisibility(!granted && !closed, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(), modifier = modifier) {
        val colors = CruiseTheme.colors
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(12.dp)
                .widthIn(max = 720.dp)
                .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
                .background(colors.surfaceElevated.copy(alpha = 0.97f))
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(painterResource(CruiseIcons.Unlock), contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.access_banner), style = CruiseTheme.typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(12.dp))
            CruisePill(stringResource(R.string.access_banner_allow), onClick = StorageAccess::request, selected = true)
            CruiseIconButton(CruiseIcons.Close, stringResource(R.string.access_banner_close), onClick = { closed = true }, size = CruiseTheme.dimens.minTouchTarget, tint = colors.textSecondary)
        }
    }
}
