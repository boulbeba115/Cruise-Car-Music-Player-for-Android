package com.boulbeba.cruise.feature.library.folders

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.data.library.FolderNode
import com.boulbeba.cruise.core.designsystem.component.CruiseIconButton
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.ui.CoverImage
import com.boulbeba.cruise.core.ui.FastScroller
import com.boulbeba.cruise.core.ui.scrollLetter
import com.boulbeba.cruise.feature.library.FAST_SCROLL_GUTTER
import com.boulbeba.cruise.core.ui.TrackItem
import com.boulbeba.cruise.feature.library.R
import com.boulbeba.cruise.feature.library.TrackMenuState
import java.io.File

/** Folders tab of the Library (SPEC §5.2, CHANGE_REQUESTS.md CR-04): the stick's music folders, playable at every level. */
@Composable
fun FoldersTab(menu: TrackMenuState, playingPath: String?, modifier: Modifier = Modifier, viewModel: FoldersViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    BackHandler(enabled = !ui.isTop) { viewModel.up() }
    val tree = ui.tree
    Column(modifier.fillMaxSize()) {
        when {
            tree == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = CruiseTheme.colors.accent) }
            tree.isEmpty -> Text(
                stringResource(R.string.folders_empty), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 24.dp),
            )
            else -> {
                val current = ui.current
                FolderHeader(current, isTop = ui.isTop, onUp = { viewModel.up() }, onPlay = { shuffle -> current?.let { viewModel.playTree(it, shuffle) } })
                AnimatedContent(current?.path, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "folder", modifier = Modifier.weight(1f)) { path ->
                    val node = path?.let(tree::node)
                    val folders = node?.children ?: tree.volumes
                    val shownTracks = if (node?.row != null) tracks else emptyList()
                    val list = rememberLazyListState()
                    Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize().padding(end = FAST_SCROLL_GUTTER)) {
                        items(folders, key = { "d:" + it.path }) { child ->
                            FolderItem(
                                child,
                                active = playingPath?.startsWith(child.path + File.separator) == true,
                                onOpen = { viewModel.open(child) },
                                onPlay = { viewModel.playTree(child) },
                            )
                        }
                        if (shownTracks.isNotEmpty()) {
                            itemsIndexed(shownTracks, key = { _, t -> "f:" + t.path }) { i, t ->
                                TrackItem(
                                    t, onClick = { viewModel.play(i) }, onLongClick = { menu.forTrack(t) },
                                    active = t.path == playingPath, showCover = false, showNumber = false, subtitle = t.artist,
                                )
                            }
                        }
                    }
                    FastScroller(
                        list, folders.size + shownTracks.size,
                        label = { i -> if (i < folders.size) scrollLetter(folders[i].name) else scrollLetter(shownTracks[i - folders.size].title) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderHeader(current: FolderNode?, isTop: Boolean, onUp: () -> Unit, onPlay: (shuffle: Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        if (!isTop) {
            CruiseIconButton(CruiseIcons.Back, stringResource(R.string.lib_back), onClick = onUp, size = CruiseTheme.dimens.minTouchTarget, tint = CruiseTheme.colors.textPrimary)
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                skinTitle(current?.let { folderLabel(it) } ?: stringResource(R.string.lib_tab_folders)),
                style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (current != null && !current.isVolume) {
                val volume = volumeLabel(current.volume)
                Text(
                    volume + current.path.removePrefix(current.volume).substringBeforeLast(File.separatorChar).replace(File.separator, "  ›  "),
                    style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (current != null) {
            Spacer(Modifier.width(12.dp))
            CruisePill(stringResource(R.string.folders_play_all), onClick = { onPlay(false) }, icon = CruiseIcons.PlayAll, selected = true)
            Spacer(Modifier.width(10.dp))
            CruisePill(stringResource(R.string.folders_shuffle), onClick = { onPlay(true) }, icon = CruiseIcons.Shuffle)
        }
    }
}

/** Internal storage, SD card or USB stick, from the volume's mount name (head units name them usbdisk0, extsd…). */
@Composable
private fun volumeLabel(volume: String): String {
    val name = volume.substringAfterLast('/').substringAfterLast('\\')
    return when {
        volume.startsWith("/storage/emulated") || volume.startsWith("/sdcard") -> stringResource(R.string.folders_internal)
        listOf("usb", "udisk", "otg").any { volume.contains(it, ignoreCase = true) } -> stringResource(R.string.folders_usb, name)
        listOf("sd", "ext", "card").any { name.contains(it, ignoreCase = true) } -> stringResource(R.string.folders_sd, name)
        else -> stringResource(R.string.folders_storage, name)
    }
}

@Composable
private fun folderLabel(node: FolderNode): String = if (node.isVolume) volumeLabel(node.path) else node.name

/** A music folder: cover of its first album, name, what it holds; tap opens it, ▶ (or long-press) plays all of it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderItem(node: FolderNode, active: Boolean, onOpen: () -> Unit, onPlay: () -> Unit) {
    val colors = CruiseTheme.colors
    val dimens = CruiseTheme.dimens
    val sample = remember(node) { firstWithMusic(node) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.listRowHeight + 8.dp)
            .clip(RoundedCornerShape(dimens.cardRadius * 0.7f))
            .background(if (active) colors.accent.copy(alpha = 0.16f) else colors.surface.copy(alpha = 0.78f))
            .combinedClickable(onClick = onOpen, onLongClick = onPlay)
            .padding(start = 12.dp, end = 4.dp),
    ) {
        val row = sample?.row
        if (row != null) {
            CoverImage(row.sampleAlbumKey, row.samplePath, row.folder, node.name, Modifier.size(56.dp), RoundedCornerShape(dimens.cardRadius * 0.5f))
        } else {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(dimens.cardRadius * 0.5f)).background(colors.surfaceElevated), contentAlignment = Alignment.Center) {
                Icon(painterResource(CruiseIcons.Folder), null, tint = colors.textSecondary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(folderLabel(node), style = CruiseTheme.typography.body, color = if (active) colors.accent else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val own = node.row
            val tracks = pluralStringResource(R.plurals.folders_tracks, node.totalTracks, node.totalTracks)
            val subtitle = when {
                node.isAlbum && own != null -> listOf(
                    own.albumArtist, own.year.takeIf { it > 0 }?.toString().orEmpty(), tracks,
                ).filter { it.isNotBlank() }.joinToString(" · ")
                node.children.isEmpty() -> tracks
                else -> tracks + " · " + pluralStringResource(R.plurals.folders_subfolders, node.children.size, node.children.size)
            }
            Text(subtitle, style = CruiseTheme.typography.meta, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CruiseIconButton(CruiseIcons.Play, stringResource(R.string.lib_play), onClick = onPlay, size = dimens.minTouchTarget, tint = colors.accent)
    }
}

private fun firstWithMusic(node: FolderNode): FolderNode? {
    if (node.row != null) return node
    for (c in node.children) firstWithMusic(c)?.let { return it }
    return null
}
