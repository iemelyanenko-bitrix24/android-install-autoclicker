package ru.installclicker

import android.content.Context

/** Настройки приложения. Читаются из памяти, поэтому опрос на каждом событии дешёвый. */
class Prefs(context: Context) {

    private val store = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Главный переключатель. Читается на каждом событии — вкл/выкл мгновенный. */
    var enabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, false)
        set(value) = store.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Работать не только в системном установщике (например, в вендорном от DesaySV). */
    var anyPackage: Boolean
        get() = store.getBoolean(KEY_ANY_PACKAGE, false)
        set(value) = store.edit().putBoolean(KEY_ANY_PACKAGE, value).apply()

    /** Дампить дерево окна в logcat, когда кнопка не найдена. */
    var verboseLog: Boolean
        get() = store.getBoolean(KEY_VERBOSE, false)
        set(value) = store.edit().putBoolean(KEY_VERBOSE, value).apply()

    /** Пробовать жест по координатам, если ACTION_CLICK не сработал. */
    var gestureFallback: Boolean
        get() = store.getBoolean(KEY_GESTURE, true)
        set(value) = store.edit().putBoolean(KEY_GESTURE, value).apply()

    private companion object {
        const val NAME = "install_clicker"
        const val KEY_ENABLED = "enabled"
        const val KEY_ANY_PACKAGE = "any_package"
        const val KEY_VERBOSE = "verbose_log"
        const val KEY_GESTURE = "gesture_fallback"
    }
}
