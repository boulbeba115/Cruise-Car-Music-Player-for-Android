package com.boulbeba.cruise.feature.folders

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.model.AudioFiles
import java.io.File

/** Folder browser (SPEC §5.2, Phase 1 version): the USB stick's own structure, playable at every level. */
@Composable
fun FoldersScreen(
    modifier: Modifier = Modifier,
    viewModel: FoldersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshRoots() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshRoots() }
    BackHandler(enabled = state.root != null) { viewModel.up() }

    val dimens = CruiseTheme.dimens
    Column(modifier.fillMaxSize().padding(horizontal = dimens.gutter)) {
        Header(state, onUp = { viewModel.up() }, onPlayAll = viewModel::playAll)
        when {
            !state.hasPermission -> PermissionCard {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                    }
                } else {
                    permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            state.root == null -> RootList(state.roots) { viewModel.open(it, it.dir) }
            else -> AnimatedContent(
                targetState = state.listing,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "folder",
                modifier = Modifier.weight(1f),
            ) { listing ->
                if (listing == null || state.loading && listing.dir != state.listing?.dir) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(listing.folders, key = { "d:" + it.path }) { dir ->
                            FolderRow(
                                icon = CruiseIcons.Folder,
                                title = dir.name,
                                trailing = CruiseIcons.Chevron,
                                active = playback.path?.startsWith(dir.path + "/") == true,
                                onClick = { viewModel.openFolder(dir) },
                                onLongClick = { viewModel.playFolderTree(dir) },
                            )
                        }
                        itemsIndexed(listing.tracks, key = { _, f -> "f:" + f.path }) { index, file ->
                            FolderRow(
                                icon = CruiseIcons.Note,
                                title = AudioFiles.displayName(file),
                                subtitle = file.extension.uppercase(),
                                active = playback.path == file.path,
                                onClick = { viewModel.playFrom(index) },
                            )
                        }
                        if (listing.folders.isEmpty() && listing.tracks.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.folders_empty),
                                    style = CruiseTheme.typography.body,
                                    color = CruiseTheme.colors.textSecondary,
                                    modifier = Modifier.padding(top = 32.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: FoldersUiState, onUp: () -> Unit, onPlayAll: (Boolean) -> Unit) {
    val dir = state.listing?.dir
    val root = state.root
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = CruiseTheme.dimens.gutter, bottom = 12.dp),
    ) {
        if (root != null) {
            CruisePill(stringResource(R.string.folders_up), onClick = onUp, icon = CruiseIcons.Back)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = when {
                    root == null -> stringResource(R.string.folders_title)
                    dir == null || dir.path == root.dir.path -> rootLabel(root)
                    else -> dir.name
                },
                style = CruiseTheme.typography.title,
                color = CruiseTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (root != null && dir != null && dir.path != root.dir.path) {
                Text(
                    rootLabel(root) + dir.path.removePrefix(root.dir.path).substringBeforeLast('/').replace("/", "  ›  "),
                    style = CruiseTheme.typography.meta,
                    color = CruiseTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.listing != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                CruisePill(stringResource(R.string.folders_play_all), onClick = { onPlayAll(false) }, icon = CruiseIcons.PlayAll, selected = true)
                CruisePill(stringResource(R.string.folders_shuffle), onClick = { onPlayAll(true) }, icon = CruiseIcons.Shuffle)
            }
        }
    }
}

@Composable
private fun rootLabel(root: StorageRoot): String =
    if (root.isRemovable) stringResource(R.string.folders_usb, root.dir.name) else stringResource(R.string.folders_internal)

@Composable
private fun RootList(roots: List<StorageRoot>, onOpen: (StorageRoot) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(roots, key = { it.dir.path }) { root ->
            val free = root.dir.freeSpace / 1e9
            val total = root.dir.totalSpace / 1e9
            FolderRow(
                icon = if (root.isRemovable) CruiseIcons.Usb else CruiseIcons.Phone,
                title = rootLabel(root),
                subtitle = stringResource(R.string.folders_space, total, free),
                trailing = CruiseIcons.Chevron,
                onClick = { onOpen(root) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    @DrawableRes icon: Int,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    @DrawableRes trailing: Int? = null,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = CruiseTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(CruiseTheme.dimens.listRowHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) colors.accent.copy(alpha = 0.16f) else colors.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (active) colors.accent else colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = if (active) colors.onAccent else colors.textSecondary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = CruiseTheme.typography.body, color = if (active) colors.accent else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = CruiseTheme.typography.meta, color = colors.textSecondary, maxLines = 1)
        }
        if (trailing != null) Icon(painterResource(trailing), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun PermissionCard(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).background(CruiseTheme.colors.surface).padding(24.dp),
    ) {
        Text(stringResource(R.string.folders_permission_title), style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.folders_permission_body), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textSecondary)
        Spacer(Modifier.height(16.dp))
        CruisePill(stringResource(R.string.folders_permission_grant), onClick = onGrant, icon = CruiseIcons.Unlock, selected = true)
    }
}

