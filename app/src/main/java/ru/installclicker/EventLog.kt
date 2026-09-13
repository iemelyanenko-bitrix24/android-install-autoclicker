package ru.installclicker

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Кольцевой буфер последних событий — для экрана диагностики.
 * Всё дублируется в logcat под тегом [TAG].
 */
object EventLog {

    const val TAG = "InstallClicker"

    private const val MAX_LINES = 40

    private val lines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(message: String) {
        lines.addLast("${timeFormat.format(Date())}  $message")
        while (lines.size > MAX_LINES) lines.removeFirst()
        Log.i(TAG, message)
    }

    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    @Synchronized
    fun clear() {
        lines.clear()
    }
}
