package com.jagaldol.dailytarot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jagaldol.dailytarot.data.CardImages
import com.jagaldol.dailytarot.model.Deck
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CardImagesTest {
    @Test
    fun widgetBitmapIsBoundedCachedAndRotatedCorrectly() = runBlocking {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val upright = CardImages.widget(resources, Deck[0].imageRes, false)
        val reversed = CardImages.widget(resources, Deck[0].imageRes, true)
        assertTrue(upright.height <= 1000)
        assertTrue(reversed.allocationByteCount <= 2_400_000)
        assertSame(reversed, CardImages.widget(resources, Deck[0].imageRes, true))
        assertEquals(upright.getPixel(50, 70), reversed.getPixel(reversed.width - 51, reversed.height - 71))
    }

    @Test
    fun allImagesResolveUnderAnArabicLocale() = runBlocking {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            for (card in Deck) {
                assertEquals("drawable", resources.getResourceTypeName(card.imageRes))
                val thumbnail = CardImages.thumbnail(resources, card.thumbnailRes)
                assertEquals(360, thumbnail.width)
                assertEquals(600, thumbnail.height)
            }
        } finally {
            Locale.setDefault(original)
        }
    }
}
