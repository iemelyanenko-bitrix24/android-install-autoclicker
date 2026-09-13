package ru.installclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Вкл/выкл автоклика без касаний экрана ГУ:
 *
 * ```
 * adb shell am broadcast -a ru.installclicker.TOGGLE --ez enabled true
 * adb shell am broadcast -a ru.installclicker.TOGGLE --ez enabled false
 * adb shell am broadcast -a ru.installclicker.TOGGLE          # переключить
 * ```
 */
class ToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return

        val prefs = Prefs(context)
        val value = if (intent.hasExtra(EXTRA_ENABLED)) {
            intent.getBooleanExtra(EXTRA_ENABLED, false)
        } else {
            !prefs.enabled
        }

        prefs.enabled = value
        EventLog.add("Автоклик ${if (value) "включён" else "выключен"} через broadcast")
    }

    companion object {
        const val ACTION_TOGGLE = "ru.installclicker.TOGGLE"
        const val EXTRA_ENABLED = "enabled"
    }
}
