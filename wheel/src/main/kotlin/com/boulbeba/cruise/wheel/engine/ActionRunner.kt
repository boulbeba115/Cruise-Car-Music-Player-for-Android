package com.boulbeba.cruise.wheel.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.view.KeyEvent
import com.boulbeba.cruise.core.car.CarLog
import com.boulbeba.cruise.core.car.calls.CallControl
import com.boulbeba.cruise.core.car.calls.CallState
import com.boulbeba.cruise.core.car.steering.SteeringHelperService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Carries out a rule's [Action]. Every action is something any app may do; nothing needs root. */
@Singleton
class ActionRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calls: CallControl,
    private val store: RuleStore,
) {
    private val audio = context.getSystemService(AudioManager::class.java)

    fun run(rule: Rule) {
        CarLog.log("wheel", "${rule.trigger} -> ${rule.action}${rule.packageName?.let { " $it" }.orEmpty()}")
        when (rule.action) {
            Action.PlayPause -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            Action.Next -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
            Action.Previous -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            Action.FastForward -> media(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            Action.Rewind -> media(KeyEvent.KEYCODE_MEDIA_REWIND)
            Action.Stop -> media(KeyEvent.KEYCODE_MEDIA_STOP)
            Action.VolumeUp -> volume(AudioManager.ADJUST_RAISE)
            Action.VolumeDown -> volume(AudioManager.ADJUST_LOWER)
            Action.Mute -> volume(AudioManager.ADJUST_TOGGLE_MUTE)
            Action.AnswerCall -> if (calls.state.value == CallState.Active) calls.hangUp() else calls.answer()
            Action.RejectCall -> calls.reject()
            Action.HangUp -> calls.hangUp()
            Action.OpenMusic -> (musicPackage() ?: cruisePackage())?.let(::openApp)
            Action.OpenApp -> rule.packageName?.let(::openApp)
            Action.Home -> if (!SteeringHelperService.home()) start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
            Action.Back -> SteeringHelperService.back()
            Action.Recents -> SteeringHelperService.recents()
            Action.Notifications -> SteeringHelperService.notifications()
            Action.VoiceAssistant -> start(Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE)) || start(Intent(Intent.ACTION_VOICE_COMMAND))
            Action.Nothing -> Unit
        }
    }

    /** Where the media actions go: the app chosen in Settings, else Cruise when installed; null = whichever plays. */
    fun musicPackage(): String? = when (val target = store.musicTarget) {
        null -> cruisePackage()
        "" -> null
        else -> target.takeIf(::installed)
    }

    /** The installed Cruise (release, or the debug build on the test emulator). */
    fun cruisePackage(): String? = CRUISE_PACKAGES.firstOrNull(::installed)

    fun installed(packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess

    /** A launchable app's name, for the screens. */
    fun appLabel(packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrNull()

    private fun media(code: Int) {
        when (val target = musicPackage()) {
            null -> system(code)
            in CRUISE_PACKAGES -> if (!toCruise(target, code)) system(code)
            else -> if (!toReceiver(target, code)) system(code)
        }
    }

    /**
     * Straight to Cruise rather than through the system's media-key routing, which goes to whichever app played last.
     * Media3's session service takes a media-button intent as a start command while Cruise runs; when it does not
     * run, Android refuses that start and Cruise's media-button receiver starts it instead — for play only, the one
     * key Media3 accepts there.
     */
    private fun toCruise(cruise: String, code: Int): Boolean {
        val service = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(cruise, CRUISE_SERVICE))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
        if (runCatching { context.startService(service) != null }.getOrDefault(false)) return true
        if (code != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return false
        return toReceiver(cruise, KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    /** To another app's media-button receiver, which also starts it when it is not running. */
    private fun toReceiver(packageName: String, code: Int): Boolean {
        val receiver = context.packageManager
            .queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName), PackageManager.GET_RESOLVED_FILTER)
            .firstOrNull()?.activityInfo ?: return false
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            context.sendBroadcast(
                Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setComponent(ComponentName(receiver.packageName, receiver.name))
                    .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(now, now, action, code, 0))
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            )
        }
        return true
    }

    /** To whichever app holds the media session, as a wheel button would. */
    private fun system(code: Int) {
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

    companion object {
        val CRUISE_PACKAGES = listOf("com.boulbeba.cruise", "com.boulbeba.cruise.debug")
        private const val CRUISE_SERVICE = "com.boulbeba.cruise.core.playback.PlaybackService"
    }
}
