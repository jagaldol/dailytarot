package com.jagaldol.dailytarot.ui

import com.jagaldol.dailytarot.data.AppSettings
import com.jagaldol.dailytarot.model.Day
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAskTest {
    private val today = Day(2026, 10, 8)

    private fun ui(revealed: Boolean = true, previous: Boolean = false) = TodayUi(
        day = today, reading = previewReading(), nameKo = "컵 페이지", defaultFortune = null,
        revealed = revealed, connected = false, waitingForLifebase = false,
        showingPrevious = previous, awaitingDraw = false, dayStartLabel = "", autoDrawLabel = null,
    )

    @Test
    fun asksOncePerCardDayWhileNoWidgetExists() {
        val fresh = AppSettings()
        assertTrue(shouldAskForWidget(false, fresh, ui()))
        // Answered today ("괜찮아요" or a cancelled add): not again today, again on the next card day.
        val answered = fresh.copy(widgetPromptAskedDay = today.toString())
        assertFalse(shouldAskForWidget(false, answered, ui()))
        assertTrue(shouldAskForWidget(false, answered, ui().copy(day = today.plusDays(1))))
    }

    @Test
    fun neverAsksWithAWidgetAfterNeverOrBeforeTodaysCardIsUp() {
        assertFalse(shouldAskForWidget(true, AppSettings(), ui()))
        assertFalse(shouldAskForWidget(null, AppSettings(), ui()))
        assertFalse(shouldAskForWidget(false, AppSettings(widgetPromptNever = true), ui()))
        assertFalse(shouldAskForWidget(false, AppSettings(), ui(revealed = false)))
        assertFalse(shouldAskForWidget(false, AppSettings(), ui(previous = true)))
        assertFalse(shouldAskForWidget(false, AppSettings(), ui().copy(reading = null)))
    }
}
