package com.boulbeba.cruise.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.data.library.FolderNode
import com.boulbeba.cruise.core.designsystem.component.CruiseIconButton
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.ui.CoverImage
import com.boulbeba.cruise.core.ui.GroupItem
import com.boulbeba.cruise.core.ui.TrackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(val actions: TrackActions) : ViewModel() {
    val query = MutableStateFlow("")
    val results = query.debounce(200).mapLatest { q -> if (q.isBlank()) emptyList() else actions.library.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** The search box in the Library header (CHANGE_REQUESTS.md CR-02). Opens focused, with the keyboard up. */
@Composable
internal fun SearchField(query: String, onQuery: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CruiseTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint), style = CruiseTheme.typography.body) },
        leadingIcon = { Icon(painterResource(CruiseIcons.Search), contentDescription = null, tint = colors.accent) },
        trailingIcon = { CruiseIconButton(CruiseIcons.Back, stringResource(R.string.lib_search_close), onClick = onClose, size = 56.dp, tint = colors.textSecondary) },
        textStyle = CruiseTheme.typography.subtitle,
        shape = RoundedCornerShape(CruiseTheme.dimens.cardRadius),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceElevated, unfocusedContainerColor = colors.surfaceElevated,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, cursorColor = colors.accent,
        ),
        modifier = modifier.height(68.dp).focusRequester(focus),
    )
}

/** Instant results across the library, grouped as folders, albums, artists and tracks (SPEC §4.5). */
@Composable
internal fun SearchResults(
    viewModel: SearchViewModel,
    menu: TrackMenuState,
    playingPath: String?,
    folders: List<FolderNode>,
    onFolder: (String) -> Unit,
    onAlbum: (key: String, title: String) -> Unit,
    onArtist: (key: String, name: String) -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val albums = remember(results) { results.distinctBy { it.albumKey }.take(20) }
    val artists = remember(results) { results.filter { it.albumArtist.isNotBlank() || it.artist.isNotBlank() }.distinctBy { it.artistKey }.take(12) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (results.isEmpty() && folders.isEmpty()) {
            item { Text(stringResource(R.string.search_none, query), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textSecondary) }
        }
        if (folders.isNotEmpty()) {
            item { Section(R.string.search_folders) }
            items(folders.take(6), key = { "d:" + it.path }) { f ->
                val sample = f.row
                GroupItem(
                    title = f.name,
                    subtitle = pluralStringResource(R.plurals.folders_tracks, f.totalTracks, f.totalTracks),
                    coverKey = sample?.sampleAlbumKey ?: f.path, coverPath = sample?.samplePath.orEmpty(), coverFolder = f.path,
                    onClick = { onFolder(f.path) },
                )
            }
        }
        if (albums.isNotEmpty()) {
            item { Section(R.string.search_albums) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(albums, key = { it.albumKey }) { t -> AlbumChip(t) { onAlbum(t.albumKey, t.album) } }
                }
            }
        }
        if (artists.isNotEmpty()) {
            item { Section(R.string.search_artists) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(artists, key = { it.artistKey }) { t ->
                        val name = t.albumArtist.ifBlank { t.artist }
                        CruisePill(name, onClick = { onArtist(t.artistKey, name) })
                    }
                }
            }
        }
        if (results.isNotEmpty()) item { Section(R.string.search_tracks) }
        itemsIndexed(results, key = { _, t -> t.path }) { i, t ->
            TrackItem(t, onClick = { viewModel.actions.play(results, i) }, onLongClick = { menu.forTrack(t) }, active = t.path == playingPath)
        }
    }
}

@Composable
private fun Section(title: Int) {
    Text(stringResource(title), style = CruiseTheme.typography.label, color = CruiseTheme.colors.accent, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

@Composable
private fun AlbumChip(t: TrackRow, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).clickable(onClick = onClick).padding(4.dp)) {
        CoverImage(t.albumKey, t.path, t.folder, t.album, Modifier.size(132.dp), RoundedCornerShape(CruiseTheme.dimens.cardRadius * 0.8f))
        Text(t.album, style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
    }
}
