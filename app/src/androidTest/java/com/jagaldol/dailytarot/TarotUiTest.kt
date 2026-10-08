package com.jagaldol.dailytarot

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jagaldol.dailytarot.model.ReadingSource
import com.jagaldol.dailytarot.work.RefreshScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the installed app's data: use a test device or emulator. */
@RunWith(AndroidJUnit4::class)
class TarotUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: TarotApplication

    @Before
    fun standaloneMode() = runBlocking {
        app = compose.activity.application as TarotApplication
        RefreshScheduler.cancelImport(app)
        app.sync.disconnect()
        val today = app.repository.drawToday()
        app.settings.markRevealed(today.day.minusDays(1).toString())
    }

    @Test
    fun cardIsRevealedThenAHandPickedCardIsSavedForToday() {
        compose.onNodeWithTag("today-card").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("today-reading")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("pick").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("cards")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("cards").performScrollToNode(hasTestTag("card-77"))
        compose.onNodeWithTag("card-77").performClick()
        compose.onNodeWithTag("reversed").performClick()
        compose.onNodeWithTag("confirm").performClick()
        compose.waitUntil(5_000) {
            runBlocking { app.repository.drawToday() }.source == ReadingSource.MANUAL
        }
        val saved = runBlocking { app.repository.drawToday() }
        assertEquals(77, saved.cardId)
        assertTrue(saved.reversed)
        assertEquals(app.catalog.entry(77, true).fortuneText, saved.headline)

        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("today-reading")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun historyListsTodaysReading() {
        val today = runBlocking { app.repository.drawToday() }
        compose.onNodeWithTag("tab-history").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("history-${today.day}")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("history-${today.day}").performClick()
        compose.onNodeWithTag("tab-history").assertDoesNotExist()
    }
}
