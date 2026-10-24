package com.boulbeba.cruise.wheel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.feature.carlab.diagnostics.DiagnosticsScreen
import com.boulbeba.cruise.wheel.R
import com.boulbeba.cruise.wheel.engine.Action
import com.boulbeba.cruise.wheel.engine.Trigger

/** Where the driver is in Cruise Wheel (CHANGE_REQUESTS.md CR-18): a small stack, Back pops it. */
sealed interface Screen {
    data object Home : Screen
    data class Button(val code: Int) : Screen
    data class PickAction(val trigger: Trigger) : Screen
    data class PickApp(val trigger: Trigger) : Screen
    data object Settings : Screen
    data object PickMusicApp : Screen
    data object Report : Screen
    data object KeyLog : Screen
    data object Tools : Screen
}

/**
 * Cruise Wheel (CHANGE_REQUESTS.md CR-10, CR-18). Home shows the driver's buttons as cards that light up when pressed
 * on the wheel; a card opens the button with its press, hold and double-press actions; an action is picked from large
 * tiles by category. Setup, music target, calls, backup and the car tools live in Settings.
 */
@Composable
fun WheelApp(viewModel: WheelViewModel = hiltViewModel()) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val musicTarget by viewModel.musicTarget.collectAsStateWithLifecycle()
    val top = stack.last()
    fun open(s: Screen) { stack += s }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { back() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSetup() }

    Box(Modifier.fillMaxSize().background(CruiseTheme.colors.background).padding(horizontal = CruiseTheme.dimens.gutter)) {
        AnimatedContent(top, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "screen") { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(viewModel, onOpenButton = { open(Screen.Button(it)) }, onSettings = { open(Screen.Settings) })
                is Screen.Button -> ButtonScreen(screen.code, viewModel, onBack = ::back, onPick = { open(Screen.PickAction(it)) })
                is Screen.PickAction -> ActionPicker(screen.trigger, viewModel, onBack = ::back, onPickApp = { open(Screen.PickApp(screen.trigger)) })
                is Screen.PickApp -> AppPicker(
                    viewModel,
                    selected = rules.firstOrNull { it.trigger == screen.trigger }?.packageName,
                    onBack = ::back,
                    onPicked = { pkg ->
                        viewModel.setAction(screen.trigger, Action.OpenApp, pkg)
                        // Back to the button: past the app list and the action picker.
                        back()
                        back()
                    },
                )
                Screen.Settings -> SettingsScreen(
                    viewModel, onBack = ::back, onPickMusicApp = { open(Screen.PickMusicApp) },
                    onReport = { open(Screen.Report) }, onKeyLog = { open(Screen.KeyLog) }, onTools = { open(Screen.Tools) },
                )
                Screen.PickMusicApp -> AppPicker(viewModel, selected = musicTarget, onBack = ::back, onPicked = { viewModel.setMusicTarget(it); back() })
                Screen.Report -> ReportScreen(viewModel, onBack = ::back)
                Screen.KeyLog -> KeyLogScreen(onBack = ::back)
                Screen.Tools -> Column {
                    TopBar(stringResource(R.string.settings_tools_title), onBack = ::back)
                    DiagnosticsScreen(includeSteering = false)
                }
            }
        }
    }
}
