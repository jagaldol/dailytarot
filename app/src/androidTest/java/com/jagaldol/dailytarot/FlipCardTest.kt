package com.jagaldol.dailytarot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jagaldol.dailytarot.data.CardImages
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.ui.FlipCard
import com.jagaldol.dailytarot.ui.theme.DailytarotTheme
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlipCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theFaceIsDecodedWhileTheBackIsStillShowing() {
        val cardId = 13
        compose.setContent {
            DailytarotTheme {
                FlipCard(
                    cardId, reversed = false, revealed = false, onClick = null, contentDescription = "card",
                    modifier = Modifier.width(240.dp),
                )
            }
        }
        // Not yet revealed, yet the large face is cached for the halfway swap.
        compose.waitUntil(5_000) { CardImages.cachedFull(Deck[cardId].imageRes) != null }
        assertNotNull(CardImages.cachedFull(Deck[cardId].imageRes))
    }
}
