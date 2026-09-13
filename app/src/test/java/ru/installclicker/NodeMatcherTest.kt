package ru.installclicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeMatcherTest {

    private fun find(root: NodeLike, requireLabel: Boolean = false) =
        NodeMatcher.findInstallButton(root, requireLabel)

    @Test
    fun `находит кнопку установки по тексту`() {
        val root = container(
            label("Установить это приложение?"),
            container(button("Отмена"), button("Установить")),
        )

        val outcome = find(root) as MatchOutcome.Found

        assertEquals("установить", outcome.label)
        assertTrue(outcome.enabled)
    }

    @Test
    fun `находит кнопку обновления`() {
        val root = container(button("Отмена"), button("Обновить"))

        val outcome = find(root)

        assertEquals("обновить", (outcome as MatchOutcome.Found).label)
    }

    @Test
    fun `находит кнопку установщика по id без совпадения текста`() {
        val root = container(
            button("Cancel", viewId = "com.android.packageinstaller:id/cancel_button"),
            button("OK", viewId = "com.android.packageinstaller:id/ok_button"),
        )

        val outcome = find(root, requireLabel = false)

        assertTrue((outcome as MatchOutcome.Found).via.contains("ok_button"))
    }

    @Test
    fun `в режиме любого приложения совпадения по id недостаточно`() {
        val root = container(button("OK", viewId = "com.example.app:id/ok_button"))

        assertEquals(MatchOutcome.NoCandidate, find(root, requireLabel = true))
    }

    @Test
    fun `не жмёт ничего в диалоге удаления`() {
        val root = container(
            label("Удалить это приложение?"),
            container(button("Отмена"), button("Удалить", viewId = "com.android.packageinstaller:id/ok_button")),
        )

        val outcome = find(root)

        assertTrue("ожидался Blocked, получено $outcome", outcome is MatchOutcome.Blocked)
    }

    @Test
    fun `проза про удаление данных не блокирует установку`() {
        val root = container(
            label("Существующие данные приложения не будут удалены"),
            button("Установить"),
        )

        val outcome = find(root)

        assertEquals("установить", (outcome as MatchOutcome.Found).label)
    }

    @Test
    fun `не выбирает Отмену`() {
        val root = container(button("Отмена"), button("Cancel"))

        assertEquals(MatchOutcome.NoCandidate, find(root))
    }

    @Test
    fun `не выбирает Готово и Открыть`() {
        val root = container(button("Готово"), button("Открыть"))

        assertEquals(MatchOutcome.NoCandidate, find(root))
    }

    @Test
    fun `поднимается до кликабельного предка`() {
        val inner = label("Установить")
        val clickableWrapper = container(inner, clickable = true)
        val root = container(clickableWrapper)

        val outcome = find(root) as MatchOutcome.Found

        assertEquals(clickableWrapper, outcome.node)
    }

    @Test
    fun `сообщает про отключённую кнопку вместо клика вслепую`() {
        val root = container(button("Установить", enabled = false))

        val outcome = find(root) as MatchOutcome.Found

        assertFalse(outcome.enabled)
    }

    @Test
    fun `предпочитает кнопку заголовку с похожим текстом`() {
        val installButton = button("Установить")
        val root = container(label("Установка"), installButton)

        val outcome = find(root)

        assertEquals(installButton, (outcome as MatchOutcome.Found).node)
    }

    @Test
    fun `нет кандидатов в обычном окне`() {
        val root = container(label("Настройки"), button("Сохранить"))

        assertEquals(MatchOutcome.NoCandidate, find(root))
    }

    @Test
    fun `метка кнопки распознаётся без учёта регистра и пробелов`() {
        assertTrue(NodeMatcher.isInstallLabel("  УСТАНОВИТЬ "))
        assertTrue(NodeMatcher.isInstallLabel("Install"))
        assertTrue(NodeMatcher.isInstallLabel("安装"))
    }

    @Test
    fun `длинный текст и вопросы метками не считаются`() {
        assertFalse(NodeMatcher.isInstallLabel("Установить это приложение?"))
        assertFalse(NodeMatcher.isInstallLabel("Установить приложение из этого источника"))
        assertFalse(NodeMatcher.isInstallLabel(null))
        assertFalse(NodeMatcher.isInstallLabel("   "))
    }
}
