package ru.installclicker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickThrottleTest {

    @Test
    fun `второй клик в окне дебаунса не проходит`() {
        val throttle = ClickThrottle(windowMs = 1500L)

        assertTrue(throttle.allow("installer|установить", 1_000L))
        assertFalse(throttle.allow("installer|установить", 1_500L))
    }

    @Test
    fun `после окна дебаунса клик снова проходит`() {
        val throttle = ClickThrottle(windowMs = 1500L)

        assertTrue(throttle.allow("installer|установить", 1_000L))
        assertTrue(throttle.allow("installer|установить", 2_600L))
    }

    @Test
    fun `разные окна не мешают друг другу`() {
        val throttle = ClickThrottle(windowMs = 1500L)

        assertTrue(throttle.allow("installer|установить", 1_000L))
        assertTrue(throttle.allow("installer|обновить", 1_050L))
    }
}
