package com.boulbeba.cruise.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.designsystem.component.CruiseIconButton
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.playback.PlayerConnection
import com.boulbeba.cruise.core.playback.PlayerUiState
import com.boulbeba.cruise.core.ui.PlaceholderCover
import com.boulbeba.cruise.feature.nowplaying.LocalCoverArt
import kotlinx.coroutines.delay

/** Bar shown under every screen except Now Playing (SPEC §5.2): what plays, with play/pause and next. */
@Composable
fun MiniPlayer(state: PlayerUiState, player: PlayerConnection, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CruiseTheme.colors
    // Its own once-a-second clock: only this bar redraws, not the screen above it.
    val now by produceState(SystemClock.elapsedRealtime(), state.isPlaying) {
        while (state.isPlaying) {
            delay(1_000)
            value = SystemClock.elapsedRealtime()
        }
    }
    val progress = if (state.durationMs > 0) (state.positionAt(now).toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = CruiseTheme.dimens.gutter, vertical = 8.dp)
            .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
            .background(colors.surfaceElevated.copy(alpha = 0.94f))
            .clickable(onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(start = 8.dp, end = 8.dp),
        ) {
            val art = LocalCoverArt.current?.image
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(CruiseTheme.dimens.coverRadius * 0.4f))) {
                if (art != null) Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else PlaceholderCover(state.path.orEmpty(), sizePx = 128)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(state.title, style = CruiseTheme.typography.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(state.artist, state.album).filter { it.isNotBlank() }.joinToString(" · "),
                    style = CruiseTheme.typography.meta,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            CruiseIconButton(
                if (state.isPlaying) CruiseIcons.Pause else CruiseIcons.Play,
                contentDescription = null,
                onClick = player::playPause,
                size = 56.dp,
                filled = true,
            )
            CruiseIconButton(CruiseIcons.Next, contentDescription = null, onClick = player::next, size = 56.dp)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(colors.divider)) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Brush.horizontalGradient(listOf(colors.accent2, colors.accent))))
        }
    }
}
