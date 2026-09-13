package ru.installclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Жмёт кнопку подтверждения установки за пользователя.
 *
 * Смысл: в диалоге `PackageInstaller` кнопка защищена
 * `android:filterTouchesWhenObscured`, и пока поверх экрана висит системный
 * слой прошивки ГУ, обычное касание отбрасывается. `ACTION_CLICK` через
 * accessibility — не касание, а действие, диспетчеризуемое системой, поэтому
 * фильтр на него не действует.
 */
class InstallClickerService : AccessibilityService() {

    private val clickThrottle = ClickThrottle(CLICK_WINDOW_MS)
    private val diagnosticThrottle = ClickThrottle(DIAGNOSTIC_WINDOW_MS)

    /**
     * `typeWindowContentChanged` прилетает пачками, а обход дерева на слабом
     * SoC ГУ не бесплатный. Изменения состояния окна не троттлятся — они редкие
     * и как раз соответствуют появлению диалога установки.
     */
    private val scanThrottle = ClickThrottle(SCAN_WINDOW_MS)

    private var prefs: Prefs? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val prefs = Prefs(this).also { this.prefs = it }
        isRunning = true
        EventLog.add(
            "Сервис доступности подключён, автоклик " +
                if (prefs.enabled) "включён" else "выключен"
        )
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        isRunning = false
        EventLog.add("Сервис доступности отключён")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val prefs = prefs ?: Prefs(this).also { this.prefs = it }
        if (!prefs.enabled) return

        val eventPackage = event.packageName?.toString().orEmpty()
        val anyPackage = prefs.anyPackage
        if (!anyPackage && !isInstallerPackage(eventPackage)) return

        val now = System.currentTimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            !scanThrottle.allow("scan|$eventPackage", now)
        ) {
            return
        }

        val root = rootInActiveWindow ?: return

        NodeScope().use { scope ->
            val rootNode = scope.wrap(root)
            when (val outcome = NodeMatcher.findInstallButton(rootNode, requireLabel = anyPackage)) {
                is MatchOutcome.Found -> {
                    val key = "$eventPackage|${outcome.label}"
                    if (!clickThrottle.allow(key, now)) return@use

                    if (!outcome.enabled) {
                        EventLog.add(
                            "[$eventPackage] кнопка «${outcome.label}» найдена (${outcome.via}), " +
                                "но isEnabled=false — система её действительно отключила"
                        )
                    }
                    val result = click(outcome.node, prefs.gestureFallback, outcome.enabled)
                    EventLog.add("[$eventPackage] «${outcome.label}» (${outcome.via}) → $result")
                }

                is MatchOutcome.Blocked -> {
                    if (diagnosticThrottle.allow("blocked|$eventPackage", now)) {
                        EventLog.add(
                            "[$eventPackage] пропуск: в окне есть опасное действие «${outcome.marker}»"
                        )
                    }
                }

                is MatchOutcome.NoClickable -> {
                    if (diagnosticThrottle.allow("noclick|$eventPackage", now)) {
                        EventLog.add(
                            "[$eventPackage] «${outcome.label}» найдена, но кликабельного узла нет"
                        )
                        if (prefs.verboseLog) dumpTree(rootNode, 0)
                    }
                }

                MatchOutcome.NoCandidate -> {
                    if (prefs.verboseLog && diagnosticThrottle.allow("nocand|$eventPackage", now)) {
                        EventLog.add("[$eventPackage] кнопка установки в окне не найдена")
                        dumpTree(rootNode, 0)
                    }
                }
            }
        }
    }

    /**
     * Сначала `ACTION_CLICK` — он обходит фильтрацию касаний. Жест по координатам
     * оставлен на случай, когда узел не принимает действие: он формирует
     * синтетическое касание и на части прошивок отбрасывается тем же фильтром.
     */
    private fun click(node: NodeLike, gestureFallback: Boolean, enabled: Boolean): String {
        val info = (node as? A11yNode)?.info ?: return "внутренняя ошибка: узел не из accessibility"

        if (enabled && info.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "ACTION_CLICK выполнен"
        }
        if (!gestureFallback) {
            return if (enabled) "ACTION_CLICK вернул false, жест выключен в настройках"
            else "клик невозможен, жест выключен в настройках"
        }
        return dispatchTap(info)
    }

    private fun dispatchTap(info: AccessibilityNodeInfo): String {
        val bounds = Rect().also { info.getBoundsInScreen(it) }
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return "клик не прошёл, границы кнопки пустые"
        }

        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS))
            .build()

        return if (dispatchGesture(gesture, null, null)) {
            "ACTION_CLICK не сработал, отправлен жест в (${bounds.centerX()}, ${bounds.centerY()})"
        } else {
            "ACTION_CLICK не сработал, жест отклонён системой"
        }
    }

    private fun dumpTree(node: NodeLike, depth: Int) {
        if (depth > DUMP_DEPTH) return

        val indent = "  ".repeat(depth)
        Log.i(
            EventLog.TAG,
            "$indent${node.className} id=${node.viewId} text=${node.text} " +
                "desc=${node.contentDescription} clickable=${node.isClickable} " +
                "enabled=${node.isEnabled} visible=${node.isVisibleToUser}"
        )
        for (i in 0 until node.childCount) {
            node.child(i)?.let { dumpTree(it, depth + 1) }
        }
    }

    private fun isInstallerPackage(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return packageName.contains("packageinstaller", ignoreCase = true) ||
            packageName.contains("installer", ignoreCase = true)
    }

    companion object {

        private const val CLICK_WINDOW_MS = 1500L
        private const val SCAN_WINDOW_MS = 250L
        private const val DIAGNOSTIC_WINDOW_MS = 3000L
        private const val TAP_DURATION_MS = 60L
        private const val DUMP_DEPTH = 12

        /** Живой ли сейчас сервис. Пишется самим сервисом. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Включён ли сервис в системных настройках доступности.
         * [isRunning] недостаточно: процесс мог быть только что поднят.
         */
        fun isEnabledInSettings(context: Context): Boolean {
            val expected = ComponentName(context, InstallClickerService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()

            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == expected
            }
        }
    }
}
