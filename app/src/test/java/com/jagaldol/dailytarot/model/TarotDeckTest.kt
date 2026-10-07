package com.jagaldol.dailytarot.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TarotDeckTest {
    @Test
    fun allCardsHaveUniqueImagesAndAppearInExactlyOneSection() {
        assertEquals((0..77).toList(), Deck.map { it.id })
        assertEquals(78, Deck.map { it.imageRes }.toSet().size)
        assertEquals(78, Deck.map { it.thumbnailRes }.toSet().size)
        assertEquals((0..77).toList(), DeckSections.flatMap { it.cards }.map { it.id }.sorted())
        assertTrue(Deck.all { displayLabel(it.id).isNotBlank() })
        assertEquals("XXI. The World", displayLabel(21))
        assertEquals("King of Swords", displayLabel(77))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSelectionsAreRejected() {
        TodaySelection(78)
    }
}
