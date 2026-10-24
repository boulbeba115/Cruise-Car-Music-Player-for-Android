package com.boulbeba.cruise.wheel.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.wheel.R
import com.boulbeba.cruise.wheel.engine.Rule
import com.boulbeba.cruise.wheel.engine.Takeover
import com.boulbeba.cruise.wheel.engine.Trigger
import com.boulbeba.cruise.wheel.engine.WheelKeys
import com.boulbeba.cruise.wheel.engine.codes
import com.boulbeba.cruise.wheel.service.WheelState
import kotlinx.coroutines.delay

/** One wheel button: what the unit lets it do, and the action for a press, a hold and a double press. */
@Composable
fun ButtonScreen(code: Int, vm: WheelViewModel, onBack: () -> Unit, onPick: (Trigger) -> Unit) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val fired by WheelState.lastFired.collectAsStateWithLifecycle()
    var lit by remember { mutableStateOf<Trigger?>(null) }
    LaunchedEffect(fired) {
        val f = fired ?: return@LaunchedEffect
        if (code !in f.trigger.codes || SystemClock.elapsedRealtime() - f.timeMs > FLASH_MS) return@LaunchedEffect
        lit = f.trigger
        delay(FLASH_MS)
        lit = null
    }
    val mine = rules.filter { code in it.trigger.codes }
    val triggers = listOf(Trigger.Tap(code), Trigger.Hold(code), Trigger.DoubleTap(code)) + mine.map { it.trigger }.filterIsInstance<Trigger.Combo>()

    Column(Modifier.fillMaxSize()) {
        TopBar(Buttons.name(code), onBack = onBack) {
            CruisePill(stringResource(R.string.button_remove), onClick = { vm.forgetButton(code); onBack() }, icon = CruiseIcons.Delete)
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 820.dp
            val triggerList: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    triggers.forEach { t ->
                        TriggerRow(code, t, mine.firstOrNull { it.trigger == t }, vm, lit = lit == t, onClick = { onPick(t) })
                    }
                    Text(stringResource(R.string.button_try), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(0.4f).verticalScroll(rememberScrollState())) { Identity(code, vm, compact = false) }
                    Column(Modifier.weight(0.6f).verticalScroll(rememberScrollState())) { triggerList() }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Identity(code, vm, compact = true)
                    triggerList()
                }
            }
        }
    }
}

/** The button's icon and how far the unit lets it go; [compact] puts them side by side to leave room for the actions. */
@Composable
private fun Identity(code: Int, vm: WheelViewModel, compact: Boolean) {
    val setup by vm.setup.collectAsStateWithLifecycle()
    val colors = CruiseTheme.colors
    val context = LocalContext.current
    val does = stringResource(Buttons.does(code))
    val takeover = WheelKeys.takeover(code)
    val (text, good) = when {
        takeover == Takeover.Full -> stringResource(R.string.takeover_full, does) to true
        takeover == Takeover.Helper && setup.helperOn -> stringResource(R.string.takeover_helper_on, does) to true
        takeover == Takeover.Helper -> stringResource(R.string.takeover_helper_off, does) to false
        else -> stringResource(R.string.takeover_shared, does) to false
    }
    val note: @Composable (Modifier) -> Unit = { modifier ->
        Row(
            verticalAlignment = Alignment.Top,
            modifier = modifier.clip(RoundedCornerShape(14.dp))
                .background((if (good) colors.success else colors.warning).copy(alpha = 0.10f)).padding(14.dp),
        ) {
            Icon(
                painterResource(if (good) CruiseIcons.Ok else CruiseIcons.Info), contentDescription = null,
                tint = if (good) colors.success else colors.warning, modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(text, style = CruiseTheme.typography.meta, color = colors.textPrimary)
        }
    }
    val helperButton: @Composable () -> Unit = {
        if (takeover == Takeover.Helper && !setup.helperOn) {
            CruisePill(stringResource(R.string.setup_helper_action), onClick = { openAccessibility(context) }, selected = true, icon = CruiseIcons.Unlock)
        }
    }
    Panel(Modifier.fillMaxWidth()) {
        if (compact) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Buttons.icon(code), size = 56.dp)
                    Spacer(Modifier.width(14.dp))
                    note(Modifier.weight(1f))
                }
                helperButton()
            }
        } else {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                IconBadge(Buttons.icon(code), size = 96.dp)
                note(Modifier.fillMaxWidth())
                helperButton()
            }
        }
    }
}

@Composable
private fun TriggerRow(code: Int, trigger: Trigger, rule: Rule?, vm: WheelViewModel, lit: Boolean, onClick: () -> Unit) {
    val colors = CruiseTheme.colors
    val caption = when {
        trigger is Trigger.Combo -> Buttons.name(trigger.codes.first { it != code })
        trigger is Trigger.Tap && WheelKeys.takeover(code) == Takeover.Shared ->
            stringResource(R.string.button_unit_also, stringResource(Buttons.does(code)))
        else -> null
    }
    Panel(Modifier.fillMaxWidth(), highlight = lit, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 92.dp).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(triggerLabel(trigger)), style = CruiseTheme.typography.subtitle, color = colors.textPrimary)
                if (caption != null) Text(caption, style = CruiseTheme.typography.meta, color = colors.textSecondary)
            }
            Spacer(Modifier.width(12.dp))
            if (rule != null) {
                val info = Actions.info(rule.action)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(50)).background(colors.accent.copy(alpha = 0.15f))
                        .heightIn(min = 48.dp).padding(horizontal = 16.dp),
                ) {
                    if (rule.action == com.boulbeba.cruise.wheel.engine.Action.OpenApp && rule.packageName != null) AppIcon(rule.packageName, 24.dp)
                    else Icon(painterResource(info.icon), contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(actionText(rule, vm), style = CruiseTheme.typography.label, color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(stringResource(R.string.not_set), style = CruiseTheme.typography.label, color = colors.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(CruiseIcons.Chevron), contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(24.dp))
        }
    }
}
