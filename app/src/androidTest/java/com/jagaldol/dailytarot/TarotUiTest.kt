package com.jagaldol.dailytarot

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jagaldol.dailytarot.data.TarotRepository
import com.jagaldol.dailytarot.data.TarotStore
import com.jagaldol.dailytarot.model.TodaySelection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TarotUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var repository: TarotRepository

    @Before
    fun resetSelection() {
        repository = (compose.activity.application as TarotApplication).repository
        compose.waitUntil(10_000) { !repository.state.value.loading }
        compose.runOnIdle {
            repository.selectCard(0)
            repository.setReversed(false)
        }
        compose.waitUntil(10_000) { !repository.state.value.saving }
    }

    @Test
    fun selectionAndOrientationSurviveActivityRecreation() {
        compose.onNodeWithTag("card-1").performClick()
        compose.onNodeWithTag("reverse").performClick()
        compose.waitUntil(10_000) { !repository.state.value.saving }
        assertEquals(TodaySelection(1, true), runBlocking { TarotStore(compose.activity).load() })
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("card-1").assertIsSelected()
        compose.onNodeWithTag("reverse").assertIsOn()
    }

    @Test
    fun lastCardCanBeSelectedAfterScrollingThroughTheDeck() {
        compose.onNodeWithTag("cards").performScrollToNode(hasTestTag("card-77"))
        compose.onNodeWithTag("card-77").performClick().assertIsSelected()
        compose.waitUntil(10_000) { !repository.state.value.saving }
        assertEquals(TodaySelection(77), runBlocking { TarotStore(compose.activity).load() })
    }
}
