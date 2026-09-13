package ru.installclicker

/** Результат поиска кнопки установки в дереве окна. */
sealed class MatchOutcome {

    /** Подходящих узлов нет. */
    object NoCandidate : MatchOutcome()

    /**
     * В окне есть опасное действие (удаление, оплата) — не жмём ничего.
     * Страховка от того, что автоклик подтвердит удаление приложения.
     */
    data class Blocked(val marker: String) : MatchOutcome()

    /** Кандидат найден, но ни он, ни его предки не кликабельны. */
    data class NoClickable(val label: String) : MatchOutcome()

    /**
     * Цель для клика найдена.
     *
     * @param enabled `false` означает, что кнопка реально отключена системой —
     *   тогда `ACTION_CLICK` бесполезен и остаётся только жест.
     */
    data class Found(
        val node: NodeLike,
        val label: String,
        val via: String,
        val enabled: Boolean,
    ) : MatchOutcome()
}

/**
 * Чистая логика поиска кнопки подтверждения установки.
 *
 * Никакого Android API — только [NodeLike].
 */
object NodeMatcher {

    /** Суффиксы `viewIdResourceName` кнопки подтверждения в известных установщиках. */
    private val INSTALL_ID_SUFFIXES = listOf(
        "ok_button",
        "continue_button",
        "install_button",
        "btn_install",
        "button_install",
        "install_confirm_ok",
    )

    /** Точные метки кнопки установки/обновления. */
    private val INSTALL_LABELS = setOf(
        "установить", "обновить", "install", "update", "安装", "更新",
    )

    /** Начала меток — покрывают «Установить обновление», «Обновление» и т. п. */
    private val INSTALL_PREFIXES = listOf(
        "установ", "обнов", "install", "update", "安装", "更新",
    )

    /**
     * Опасные действия. Если в окне есть такая кнопка — автоклик молчит целиком.
     * Диалог удаления приложения тоже использует `ok_button`, поэтому одного
     * совпадения по id недостаточно для безопасности.
     */
    private val ABORT_MARKERS = listOf(
        "удал", "uninstall", "delete", "remove",
        "оплат", "купить", "buy", "pay", "подписк", "subscribe",
    )

    /** Метки, которые сами по себе никогда не являются целью. */
    private val LABEL_DENY = ABORT_MARKERS + listOf(
        "отмен", "cancel", "назад", "back", "закрыть", "close",
        "готов", "done", "открыть", "open", "нет", "no",
    )

    private const val MAX_DEPTH = 40
    private const val MAX_ASCEND = 3
    private const val MAX_LABEL_LENGTH = 30
    private const val MAX_LABEL_WORDS = 3

    private data class Candidate(
        val node: NodeLike,
        val label: String,
        val via: String,
        val score: Int,
    )

    /**
     * Ищет кнопку установки в дереве окна.
     *
     * @param requireLabel требовать совпадения по тексту, а не только по id.
     *   Включается в режиме «работать в любом приложении», где совпадение по
     *   generic-id вроде `ok_button` слишком рискованно.
     */
    fun findInstallButton(root: NodeLike, requireLabel: Boolean): MatchOutcome {
        unsafeActionMarker(root, 0)?.let { return MatchOutcome.Blocked(it) }

        val candidates = ArrayList<Candidate>()
        collect(root, 0, candidates, requireLabel)
        val best = candidates.maxByOrNull { it.score } ?: return MatchOutcome.NoCandidate

        val target = resolveClickable(best.node) ?: return MatchOutcome.NoClickable(best.label)
        return MatchOutcome.Found(
            node = target.first,
            label = best.label,
            via = best.via,
            enabled = target.second,
        )
    }

    /** Проверяет метку на пригодность в качестве кнопки установки. */
    fun isInstallLabel(raw: String?): Boolean {
        val label = normalize(raw) ?: return false
        if (label.contains('?')) return false
        if (label.length > MAX_LABEL_LENGTH) return false
        if (label.split(' ').size > MAX_LABEL_WORDS) return false
        if (LABEL_DENY.any { label.contains(it) }) return false
        if (label in INSTALL_LABELS) return true
        return INSTALL_PREFIXES.any { label.startsWith(it) }
    }

    /**
     * Ищет в дереве кнопку опасного действия.
     * Требование кликабельности (или класса Button) отсекает прозу вида
     * «существующие данные не будут удалены».
     */
    private fun unsafeActionMarker(node: NodeLike, depth: Int): String? {
        if (depth > MAX_DEPTH) return null

        val label = normalize(labelOf(node))
        if (label != null &&
            label.length <= MAX_LABEL_LENGTH &&
            label.split(' ').size <= MAX_LABEL_WORDS &&
            (node.isClickable || isButtonClass(node.className))
        ) {
            ABORT_MARKERS.firstOrNull { label.contains(it) }?.let { return label }
        }

        for (i in 0 until node.childCount) {
            val child = node.child(i) ?: continue
            unsafeActionMarker(child, depth + 1)?.let { return it }
        }
        return null
    }

    private fun collect(
        node: NodeLike,
        depth: Int,
        out: MutableList<Candidate>,
        requireLabel: Boolean,
    ) {
        if (depth > MAX_DEPTH) return

        val label = labelOf(node)
        val labelMatches = isInstallLabel(label)
        val idMatches = isInstallId(node.viewId)

        if (labelMatches || (idMatches && !requireLabel)) {
            var score = 0
            if (idMatches) score += 50
            if (labelMatches) score += 30
            if (node.isClickable) score += 20
            if (isButtonClass(node.className)) score += 15
            if (node.isVisibleToUser) score += 5

            out.add(
                Candidate(
                    node = node,
                    label = normalize(label) ?: node.viewId.orEmpty(),
                    via = if (labelMatches) "text=${normalize(label)}" else "id=${node.viewId}",
                    score = score,
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.child(i) ?: continue
            collect(child, depth + 1, out, requireLabel)
        }
    }

    /**
     * Возвращает узел, которому имеет смысл отправить клик, и признак того,
     * что он включён. Сначала ищет кликабельный и включённый узел, и только
     * потом — кликабельный, но отключённый (чтобы диагностика это показала).
     */
    private fun resolveClickable(node: NodeLike): Pair<NodeLike, Boolean>? {
        clickableChain(node) { it.isClickable && it.isEnabled }?.let { return it to true }
        clickableChain(node) { it.isClickable }?.let { return it to false }
        return null
    }

    private fun clickableChain(node: NodeLike, accept: (NodeLike) -> Boolean): NodeLike? {
        if (accept(node)) return node
        var current = node.parentNode()
        var levels = 0
        while (current != null && levels < MAX_ASCEND) {
            if (accept(current)) return current
            current = current.parentNode()
            levels++
        }
        return null
    }

    private fun isInstallId(viewId: String?): Boolean {
        val id = viewId?.substringAfterLast('/')?.lowercase() ?: return false
        return INSTALL_ID_SUFFIXES.any { id == it }
    }

    private fun isButtonClass(className: String?): Boolean {
        val name = className?.lowercase() ?: return false
        return name.contains("button")
    }

    private fun labelOf(node: NodeLike): String? =
        node.text?.takeIf { it.isNotBlank() } ?: node.contentDescription

    private fun normalize(raw: String?): String? =
        raw?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
}
