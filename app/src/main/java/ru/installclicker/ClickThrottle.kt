package ru.installclicker

/**
 * Дебаунс кликов.
 *
 * `typeWindowContentChanged` прилетает пачками, а после клика окно ещё
 * какое-то время живо — без дебаунса сервис уйдёт в цикл повторных нажатий.
 */
class ClickThrottle(private val windowMs: Long = 1500L) {

    private val lastClickAt = HashMap<String, Long>()

    @Synchronized
    fun allow(key: String, now: Long): Boolean {
        val previous = lastClickAt[key]
        if (previous != null && now - previous < windowMs) return false

        lastClickAt[key] = now
        if (lastClickAt.size > 32) {
            lastClickAt.entries.removeAll { now - it.value > windowMs * 10 }
        }
        return true
    }

    @Synchronized
    fun reset() = lastClickAt.clear()
}
