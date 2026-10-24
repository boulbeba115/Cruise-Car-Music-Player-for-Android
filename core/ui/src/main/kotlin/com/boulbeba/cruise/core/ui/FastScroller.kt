package com.boulbeba.cruise.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The letter shown while fast-scrolling: the first letter or digit of [s], Latin accents folded ("É" → "E"), digits as
 * "#", any other script (Arabic…) as it is.
 */
fun scrollLetter(s: String): String {
    val c = s.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: return "#"
    if (c.isDigit()) return "#"
    val folded = when (c) { 'À', 'Á', 'Â', 'Ä', 'Ã' -> 'A'; 'É', 'È', 'Ê', 'Ë' -> 'E'; 'Î', 'Ï', 'Í' -> 'I'; 'Ô', 'Ö', 'Ó' -> 'O'; 'Ù', 'Û', 'Ü', 'Ú' -> 'U'; 'Ç' -> 'C'; 'Ñ' -> 'N'; else -> c }
    return folded.toString()
}

/** [FastScroller] for a LazyColumn; [label] gives the bubble text of an item index. */
@Composable
fun FastScroller(state: LazyListState, itemCount: Int, label: (Int) -> String, modifier: Modifier = Modifier) {
    val visible by remember(state) { derivedStateOf { state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1) } }
    FastScroller(itemCount, { state.firstVisibleItemIndex }, visible, state.isScrollInProgress, { state.scrollToItem(it) }, label, modifier)
}

/** [FastScroller] for a LazyVerticalGrid. */
@Composable
fun FastScroller(state: LazyGridState, itemCount: Int, label: (Int) -> String, modifier: Modifier = Modifier) {
    val visible by remember(state) { derivedStateOf { state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1) } }
    FastScroller(itemCount, { state.firstVisibleItemIndex }, visible, state.isScrollInProgress, { state.scrollToItem(it) }, label, modifier)
}

/**
 * Fast scroller for long lists (owner's request 2026-10-24, CHANGE_REQUESTS.md CR-14): a slim handle on the right edge
 * that follows the list; drag it (or tap the track) to fly through the list while a big bubble beside the finger shows
 * the letter of the songs there. Appears while the list moves or is touched, fades out after a moment. Hidden for
 * short lists. Place it over the list, aligned to the end.
 */
@Composable
fun FastScroller(
    itemCount: Int,
    firstVisible: () -> Int,
    visibleCount: Int,
    scrolling: Boolean,
    scrollTo: suspend (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    if (itemCount < MIN_ITEMS || itemCount <= visibleCount) return
    val colors = CruiseTheme.colors
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) shown = true else { delay(1_500); shown = false }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "fastScroller")
    val listFraction = (firstVisible().toFloat() / (itemCount - visibleCount).coerceAtLeast(1)).coerceIn(0f, 1f)
    val fraction = if (dragging) dragFraction else listFraction
    BoxWithConstraints(modifier.fillMaxHeight().width(TOUCH_WIDTH + BUBBLE + 16.dp)) {
        val trackHeight = maxHeight - THUMB_HEIGHT
        val density = LocalDensity.current
        fun indexAt(f: Float) = (f * (itemCount - 1)).toInt().coerceIn(0, itemCount - 1)
        fun moveTo(y: Float, height: Int) {
            val thumb = with(density) { THUMB_HEIGHT.toPx() }
            dragFraction = ((y - thumb / 2) / (height - thumb)).coerceIn(0f, 1f)
            scope.launch { scrollTo(indexAt(dragFraction)) }
        }
        // Touch strip along the right edge.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(TOUCH_WIDTH)
                .fillMaxHeight()
                .pointerInput(itemCount) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; moveTo(it.y, size.height) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ -> moveTo(change.position.y, size.height) }
                }
                .pointerInput(itemCount) { detectTapGestures(onPress = { if (shown) { dragging = true; moveTo(it.y, size.height); tryAwaitRelease(); dragging = false } }) },
        ) {
            if (alpha > 0f) {
                Box(Modifier.align(Alignment.TopCenter).width(4.dp).fillMaxHeight().padding(vertical = 8.dp).clip(RoundedCornerShape(2.dp)).background(colors.textSecondary.copy(alpha = 0.18f * alpha)))
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = trackHeight * fraction)
                        .width(if (dragging) 10.dp else 7.dp)
                        .height(THUMB_HEIGHT)
                        .clip(RoundedCornerShape(50))
                        .background(colors.accent.copy(alpha = alpha * if (dragging) 1f else 0.75f)),
                )
            }
        }
        // The letter bubble beside the finger, a teardrop pointing at the handle.
        AnimatedVisibility(dragging, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopStart).offset(y = (trackHeight * fraction - BUBBLE / 2 + THUMB_HEIGHT / 2).coerceAtLeast(0.dp))) {
            Box(
                Modifier.size(BUBBLE).clip(RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 50, bottomEndPercent = 8)).background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(indexAt(fraction)), style = CruiseTheme.typography.display, color = colors.onAccent, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

private const val MIN_ITEMS = 20
private val TOUCH_WIDTH: Dp = 36.dp
private val THUMB_HEIGHT: Dp = 52.dp
private val BUBBLE: Dp = 84.dp
