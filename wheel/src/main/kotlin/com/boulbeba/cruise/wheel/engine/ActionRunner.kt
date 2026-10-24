package com.boulbeba.cruise.wheel.engine

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.view.KeyEvent
import com.boulbeba.cruise.core.car.CarLog
import com.boulbeba.cruise.core.car.calls.CallControl
import com.boulbeba.cruise.core.car.steering.SteeringHelperService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Carries out a rule's [Action]. Every action is something any app may do; nothing needs root. */
@Singleton
class ActionRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calls: CallControl,
) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun run(rule: Rule) {
        CarLog.log("wheel", "${rule.trigger} -> ${rule.action}${rule.packageName?.let { " $it" }.orEmpty()}")
        when (rule.action) {
            Action.PlayPause -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            Action.Next -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            Action.Previous -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            Action.Stop -> mediaKey(KeyEvent.KEYCODE_MEDIA_STOP)
            Action.VolumeUp -> volume(AudioManager.ADJUST_RAISE)
            Action.VolumeDown -> volume(AudioManager.ADJUST_LOWER)
            Action.Mute -> volume(AudioManager.ADJUST_TOGGLE_MUTE)
            Action.AnswerCall -> calls.answer()
            Action.RejectCall -> calls.reject()
            Action.HangUp -> calls.hangUp()
            Action.OpenApp -> rule.packageName?.let(::openApp)
            Action.Home -> if (!SteeringHelperService.home()) start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            Action.Back -> SteeringHelperService.back()
            Action.VoiceAssistant -> start(Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE)) || start(Intent(Intent.ACTION_VOICE_COMMAND))
            Action.Nothing -> Unit
        }
    }

    /** To whichever app holds the media session, as a wheel button would. */
    private fun mediaKey(code: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun volume(direction: Int) = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)

    private fun openApp(packageName: String) {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let(::start)
    }

    private fun start(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
