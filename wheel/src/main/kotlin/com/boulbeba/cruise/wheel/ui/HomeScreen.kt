package com.boulbeba.cruise.wheel.ui

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.component.CruiseIconButton
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.wheel.R
import com.boulbeba.cruise.wheel.engine.Rule
import com.boulbeba.cruise.wheel.engine.Trigger
import com.boulbeba.cruise.wheel.engine.WheelKeys
import com.boulbeba.cruise.wheel.engine.codes
import com.boulbeba.cruise.wheel.service.WheelService
import com.boulbeba.cruise.wheel.service.WheelState
import kotlinx.coroutines.delay

/** How long a card or a line stays lit after its button was pressed on the wheel. */
const val FLASH_MS = 1_400L

@Composable
fun HomeScreen(vm: WheelViewModel, onOpenButton: (Int) -> Unit, onSettings: () -> Unit) {
    val buttons by vm.buttons.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val keys by WheelState.keys.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val colors = CruiseTheme.colors

    // The card of the button just pressed lights up; an unknown button offers to be set up.
    var lit by remember { mutableStateOf<Int?>(null) }
    var fresh by remember { mutableStateOf<Int?>(null) }
    val last = keys.lastOrNull()
    LaunchedEffect(last) {
        if (last == null || SystemClock.elapsedRealtime() - last.timeMs > FLASH_MS || adding) return@LaunchedEffect
        if (last.code in buttons) {
            lit = last.code
            delay(FLASH_MS)
            lit = null
        } else {
            fresh = last.code
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.wheel_app_name), onBack = null) {
            Status(vm)
            CruiseIconButton(CruiseIcons.Settings, stringResource(R.string.home_settings), onClick = onSettings, size = CruiseTheme.dimens.minTouchTarget)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 250.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (!setup.complete) item(span = { GridItemSpan(maxLineSpan) }) { SetupCard(setup, vm) }
            fresh?.let { code ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NewButtonBanner(code, onSetUp = { fresh = null; vm.addButton(code); onOpenButton(code) }, onDismiss = { fresh = null })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.home_buttons), style = CruiseTheme.typography.subtitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.home_hint), style = CruiseTheme.typography.meta, color = colors.textSecondary, textAlign = TextAlign.End)
                }
            }
            items(buttons, key = { it }) { code ->
                ButtonCard(code, rules.filter { code in it.trigger.codes }, vm, highlight = lit == code, onClick = { onOpenButton(code) })
            }
            item(key = "add") { AddCard(onClick = { adding = true }) }
        }
    }
    if (adding) {
        AddButtonOverlay(
            taken = buttons,
            onPicked = { code -> adding = false; vm.addButton(code); onOpenButton(code) },
            onCancel = { adding = false },
        )
    }
}

@Composable
private fun Status(vm: WheelViewModel) {
    val running by WheelState.running.collectAsStateWithLifecycle()
    val connected by WheelState.connected.collectAsStateWithLifecycle()
    val colors = CruiseTheme.colors
    val context = LocalContext.current
    when {
        !running -> StatusChip(stringResource(R.string.status_stopped), colors.error, onClick = { WheelService.start(context) })
        !vm.isTwUnit -> StatusChip(stringResource(R.string.status_no_wheel), colors.warning)
        !connected -> StatusChip(stringResource(R.string.status_link_off), colors.warning)
        else -> StatusChip(stringResource(R.string.status_active), colors.success)
    }
}

@Composable
private fun SetupCard(setup: SetupState, vm: WheelViewModel) {
    val context = LocalContext.current
    val colors = CruiseTheme.colors
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.setup_title), style = CruiseTheme.typography.subtitle, color = colors.textPrimary)
            Text(stringResource(R.string.setup_body), style = CruiseTheme.typography.meta, color = colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            if (setup.keepAliveApplies) {
                SetupStep(
                    done = !setup.keepAliveNeeded,
                    title = stringResource(R.string.setup_keep_title),
                    body = stringResource(if (setup.keepAliveRefused) R.string.setup_keep_refused else R.string.setup_keep_body),
                    action = stringResource(R.string.setup_keep_action),
                    onAction = { vm.setKeepAlive(true) },
                )
                Spacer(Modifier.height(8.dp))
            }
            SetupStep(
                done = setup.helperOn,
                title = stringResource(R.string.setup_helper_title),
                body = stringResource(R.string.setup_helper_body),
                action = stringResource(R.string.setup_helper_action),
                onAction = { openAccessibility(context) },
            )
        }
    }
}

@Composable
private fun SetupStep(done: Boolean, title: String, body: String, action: String, onAction: () -> Unit) {
    val colors = CruiseTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceElevated).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        IconBadge(if (done) CruiseIcons.Ok else CruiseIcons.Error, tint = if (done) colors.success else colors.warning, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = CruiseTheme.typography.body, color = colors.textPrimary)
            Text(body, style = CruiseTheme.typography.meta, color = colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        if (done) Text(stringResource(R.string.setup_done), style = CruiseTheme.typography.label, color = colors.success)
        else CruisePill(action, onClick = onAction, selected = true)
    }
}

@Composable
private fun NewButtonBanner(code: Int, onSetUp: () -> Unit, onDismiss: () -> Unit) {
    val colors = CruiseTheme.colors
    Panel(Modifier.fillMaxWidth(), highlight = true) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            IconBadge(Buttons.icon(code), size = 44.dp)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.home_new_button, Buttons.name(code)), style = CruiseTheme.typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
            CruisePill(stringResource(R.string.home_set_up), onClick = onSetUp, selected = true)
            Spacer(Modifier.width(8.dp))
            CruiseIconButton(CruiseIcons.Close, null, onClick = onDismiss, size = CruiseTheme.dimens.minTouchTarget)
        }
    }
}

@Composable
private fun ButtonCard(code: Int, rules: List<Rule>, vm: WheelViewModel, highlight: Boolean, onClick: () -> Unit) {
    val colors = CruiseTheme.colors
    // Press, hold, double press, in that order; combinations from an imported file come last.
    val shown = rules.sortedBy { r -> listOf(Trigger.Tap::class, Trigger.Hold::class, Trigger.DoubleTap::class).indexOf(r.trigger::class).let { if (it < 0) 9 else it } }
    Panel(Modifier.fillMaxWidth().height(168.dp), highlight = highlight, onClick = onClick) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Buttons.icon(code), size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Text(Buttons.name(code), style = CruiseTheme.typography.subtitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(painterResource(CruiseIcons.Chevron), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(14.dp))
            if (shown.isEmpty()) {
                Text(stringResource(R.string.home_default), style = CruiseTheme.typography.meta, color = colors.textSecondary)
            }
            shown.take(3).forEachIndexed { i, rule ->
                if (i == 2 && shown.size > 3) {
                    Text(stringResource(R.string.home_more, shown.size - 2), style = CruiseTheme.typography.meta, color = colors.textSecondary)
                } else {
                    RuleLine(rule, vm)
                }
            }
        }
    }
}

@Composable
private fun RuleLine(rule: Rule, vm: WheelViewModel) {
    val colors = CruiseTheme.colors
    val info = Actions.info(rule.action)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(stringResource(triggerLabel(rule.trigger)), style = CruiseTheme.typography.meta, color = colors.textSecondary, modifier = Modifier.widthIn(min = 88.dp))
        Icon(painterResource(info.icon), contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(actionText(rule, vm), style = CruiseTheme.typography.meta, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Play / pause", or the app's name for "Open an app". */
@Composable
fun actionText(rule: Rule, vm: WheelViewModel): String =
    if (rule.action == com.boulbeba.cruise.wheel.engine.Action.OpenApp && rule.packageName != null) vm.appLabel(rule.packageName)
    else stringResource(Actions.info(rule.action).label)

@Composable
private fun AddCard(onClick: () -> Unit) {
    val colors = CruiseTheme.colors
    val shape = RoundedCornerShape(CruiseTheme.dimens.cardRadius)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(shape)
            .border(2.dp, colors.divider, shape)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        IconBadge(R.drawable.ic_add, size = 56.dp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.home_add), style = CruiseTheme.typography.body, color = colors.textPrimary)
        Text(stringResource(R.string.home_add_hint), style = CruiseTheme.typography.meta, color = colors.textSecondary)
    }
}

/** "Press the button on the steering wheel": the next button pressed is added; the list is for devices without a wheel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddButtonOverlay(taken: List<Int>, onPicked: (Int) -> Unit, onCancel: () -> Unit) {
    val keys by WheelState.keys.collectAsStateWithLifecycle()
    val connected by WheelState.connected.collectAsStateWithLifecycle()
    val openedAt = remember { SystemClock.elapsedRealtime() }
    LaunchedEffect(keys) {
        keys.lastOrNull()?.takeIf { it.timeMs >= openedAt }?.let { onPicked(it.code) }
    }
    val colors = CruiseTheme.colors
    val pulse by rememberInfiniteTransition(label = "listen").animateFloat(1f, 1.18f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.85f)).clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 760.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
                .background(colors.surface)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(28.dp),
        ) {
            Box(
                Modifier.size(104.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }.clip(CircleShape).background(colors.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_wheel), contentDescription = null, tint = colors.accent, modifier = Modifier.size(60.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.add_title), style = CruiseTheme.typography.title, color = colors.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (connected) R.string.add_body else R.string.add_no_wheel),
                style = CruiseTheme.typography.body, color = colors.textSecondary, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WheelKeys.known.filter { it !in taken }.forEach { code ->
                    CruisePill(Buttons.name(code), onClick = { onPicked(code) }, icon = Buttons.icon(code))
                }
            }
            Spacer(Modifier.height(18.dp))
            CruisePill(stringResource(R.string.add_cancel), onClick = onCancel)
        }
    }
}

fun openAccessibility(context: android.content.Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
