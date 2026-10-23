package com.boulbeba.cruise.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.ui.CoverImage
import com.boulbeba.cruise.core.ui.NavRequests
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

/** Offline search across the whole library (SPEC §4.5): results grouped as albums, artists and tracks. */
@Composable
fun SearchScreen(onOpenLibrary: () -> Unit, modifier: Modifier = Modifier, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val playing by viewModel.actions.player.state.collectAsStateWithLifecycle()
    val menu = rememberTrackMenu()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val colors = CruiseTheme.colors
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = CruiseTheme.dimens.gutter)) {
            Text(skinTitle(stringResource(R.string.search_title)), style = CruiseTheme.typography.title, color = colors.textPrimary, modifier = Modifier.padding(top = CruiseTheme.dimens.gutter, bottom = 12.dp))
            TextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint), style = CruiseTheme.typography.body) },
                leadingIcon = { Icon(painterResource(CruiseIcons.Search), contentDescription = null, tint = colors.accent) },
                textStyle = CruiseTheme.typography.subtitle,
                shape = RoundedCornerShape(CruiseTheme.dimens.cardRadius),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceElevated, unfocusedContainerColor = colors.surfaceElevated,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary, cursorColor = colors.accent,
                ),
                modifier = Modifier.fillMaxWidth().height(68.dp).focusRequester(focus),
            )
            Spacer(Modifier.height(12.dp))
            if (query.isNotBlank() && results.isEmpty()) {
                Text(stringResource(R.string.search_none, query), style = CruiseTheme.typography.body, color = colors.textSecondary)
            }
            val albums = remember(results) { results.distinctBy { it.albumKey }.take(20) }
            val artists = remember(results) { results.map { it.artist }.filter { it.isNotBlank() }.distinct().take(12) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (albums.size > 1) {
                    item { Section(R.string.search_albums) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(albums, key = { it.albumKey }) { t -> AlbumChip(t) { NavRequests.album.value = t.albumKey; onOpenLibrary() } }
                        }
                    }
                }
                if (artists.isNotEmpty()) {
                    item { Section(R.string.search_artists) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(artists, key = { it }) { a ->
                                com.boulbeba.cruise.core.designsystem.component.CruisePill(a, onClick = { viewModel.query.value = a })
                            }
                        }
                    }
                }
                if (results.isNotEmpty()) item { Section(R.string.search_tracks) }
                itemsIndexed(results, key = { _, t -> t.path }) { i, t ->
                    TrackItem(t, onClick = { viewModel.actions.play(results, i) }, onLongClick = { menu.forTrack(t) }, active = t.path == playing.libraryPath)
                }
            }
        }
        TrackMenuHost(menu, viewModel.actions)
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
        Text(t.album, style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

