package ru.installclicker

/** Узел UI-дерева для тестов: собирается литералом, родитель проставляется сам. */
class FakeNode(
    override val viewId: String? = null,
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val className: String? = null,
    override val isClickable: Boolean = false,
    override val isEnabled: Boolean = true,
    override val isVisibleToUser: Boolean = true,
    private val children: List<FakeNode> = emptyList(),
) : NodeLike {

    private var parent: FakeNode? = null

    init {
        children.forEach { it.parent = this }
    }

    override val childCount: Int get() = children.size

    override fun child(index: Int): NodeLike? = children.getOrNull(index)

    override fun parentNode(): NodeLike? = parent
}

fun button(
    text: String,
    viewId: String? = null,
    enabled: Boolean = true,
    clickable: Boolean = true,
) = FakeNode(
    viewId = viewId,
    text = text,
    className = "android.widget.Button",
    isClickable = clickable,
    isEnabled = enabled,
)

fun label(text: String) = FakeNode(text = text, className = "android.widget.TextView")

fun container(vararg children: FakeNode, clickable: Boolean = false) = FakeNode(
    className = "android.widget.LinearLayout",
    isClickable = clickable,
    children = children.toList(),
)
