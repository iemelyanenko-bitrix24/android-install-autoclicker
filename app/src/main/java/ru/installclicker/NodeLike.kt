package ru.installclicker

/**
 * Абстракция узла UI-дерева.
 *
 * Нужна, чтобы логика поиска кнопки установки ([NodeMatcher]) не зависела от
 * `AccessibilityNodeInfo` и покрывалась обычными JVM-тестами.
 */
interface NodeLike {
    val viewId: String?
    val text: String?
    val contentDescription: String?
    val className: String?
    val isClickable: Boolean
    val isEnabled: Boolean
    val isVisibleToUser: Boolean
    val childCount: Int

    fun child(index: Int): NodeLike?
    fun parentNode(): NodeLike?
}
