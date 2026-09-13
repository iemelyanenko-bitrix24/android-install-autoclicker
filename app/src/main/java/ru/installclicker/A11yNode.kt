package ru.installclicker

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.io.Closeable

/**
 * Держит все обёрнутые узлы и освобождает их на выходе.
 *
 * До Android 13 `AccessibilityNodeInfo` требует ручного `recycle()`, иначе
 * системный пул узлов исчерпывается и события начинают теряться.
 */
class NodeScope : Closeable {

    private val tracked = ArrayList<AccessibilityNodeInfo>()

    fun wrap(info: AccessibilityNodeInfo): A11yNode {
        tracked.add(info)
        return A11yNode(info, this)
    }

    fun wrapOrNull(info: AccessibilityNodeInfo?): A11yNode? = info?.let { wrap(it) }

    override fun close() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            tracked.forEach { runCatching { @Suppress("DEPRECATION") it.recycle() } }
        }
        tracked.clear()
    }
}

/** Адаптер [AccessibilityNodeInfo] к [NodeLike]. */
class A11yNode(
    val info: AccessibilityNodeInfo,
    private val scope: NodeScope,
) : NodeLike {

    override val viewId: String? get() = info.viewIdResourceName
    override val text: String? get() = info.text?.toString()
    override val contentDescription: String? get() = info.contentDescription?.toString()
    override val className: String? get() = info.className?.toString()
    override val isClickable: Boolean get() = info.isClickable
    override val isEnabled: Boolean get() = info.isEnabled
    override val isVisibleToUser: Boolean get() = info.isVisibleToUser
    override val childCount: Int get() = info.childCount

    override fun child(index: Int): NodeLike? = scope.wrapOrNull(info.getChild(index))

    override fun parentNode(): NodeLike? = scope.wrapOrNull(info.parent)
}
