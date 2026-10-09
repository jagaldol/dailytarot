package com.jagaldol.dailytarot.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTextTest {
    @Test
    fun wordJoinersOnlyBindLettersWithinWords() {
        val joined = keepWordsTogether("오늘은 천천히")
        assertEquals("오⁠늘⁠은 천⁠천⁠히", joined)
        assertEquals("오늘은 천천히", joined.replace("⁠", ""))
    }

    private val line = "지쳐 있다면, 오늘은 욕심내지 말고 천천히 자신을 회복시키세요"

    @Test
    fun captionLinesWrapAtSpacesAndGrowWhenNarrowerOrLarger() {
        assertEquals(0, captionLines(null, 300f, 13f, 1f))
        assertEquals(1, captionLines("한 줄", 300f, 13f, 1f))
        val wide = captionLines(line, 340f, 13f, 1f)
        assertEquals(2, wide)
        assertTrue(captionLines(line, 160f, 13f, 1f) > wide)
        assertTrue(captionLines(line, 340f, 13f, 1.3f) >= wide)
        assertTrue(captionLines(line, 160f, 10f, 1f) <= captionLines(line, 160f, 13f, 1f))
    }

    @Test
    fun captionShrinksBeforeItIsDropped() {
        val roomy = planWidget(348f, 548f, line, 1f) as WidgetPlan.Stacked
        assertEquals(13f, roomy.style.lineSp)
        val tight = planWidget(160f, 200f, line, 1f)
        assertTrue(tight is WidgetPlan.Stacked && tight.style.lineSp < 13f)
        assertTrue(planWidget(318f, 148f, line, 1f) is WidgetPlan.Side)
        assertTrue(planWidget(138f, 138f, line, 1f) is WidgetPlan.CardOnly)
    }

    @Test
    fun latinWidthsNeverFallBelowNotoSerif() {
        // Advance widths in em of NotoSerif-Regular.ttf (Android's serif family) for printable ASCII.
        val measured = "!\"#\$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~".toList().zip(
            listOf(0.333f, 0.408f, 0.559f, 0.559f, 0.896f, 0.742f, 0.22f, 0.346f, 0.346f, 0.5f, 0.559f, 0.25f, 0.31f, 0.25f, 0.288f, 0.559f, 0.559f, 0.559f, 0.559f, 0.559f, 0.559f, 0.559f, 0.559f, 0.559f, 0.559f, 0.286f, 0.286f, 0.559f, 0.559f, 0.559f, 0.5f, 0.921f, 0.705f, 0.654f, 0.614f, 0.727f, 0.623f, 0.59f, 0.714f, 0.793f, 0.367f, 0.357f, 0.7f, 0.623f, 0.938f, 0.763f, 0.742f, 0.604f, 0.742f, 0.656f, 0.544f, 0.613f, 0.717f, 0.675f, 1.047f, 0.66f, 0.625f, 0.592f, 0.36f, 0.288f, 0.36f, 0.559f, 0.459f, 0.577f, 0.563f, 0.614f, 0.492f, 0.614f, 0.535f, 0.369f, 0.538f, 0.635f, 0.32f, 0.3f, 0.585f, 0.31f, 0.945f, 0.645f, 0.577f, 0.614f, 0.614f, 0.471f, 0.451f, 0.352f, 0.635f, 0.579f, 0.862f, 0.578f, 0.565f, 0.511f, 0.428f, 0.559f, 0.428f, 0.559f),
        )
        for ((char, width) in measured) assertTrue("$char", glyphEms(char) >= width)
    }

    @Test
    fun englishCaptionsFitLikeKoreanOnes() {
        val english = "If you are worn out, ask less of yourself and take time to recover today."
        assertTrue(planWidget(348f, 548f, english, 1f) is WidgetPlan.Stacked)
        val tight = planWidget(160f, 200f, english, 1f)
        assertTrue(tight is WidgetPlan.Stacked)
    }

    @Test
    fun everyCaptionThatIsShownFitsItsCell() {
        for (w in 100..400 step 20) for (h in 100..600 step 20) {
            when (val plan = planWidget(w.toFloat(), h.toFloat(), line, 1f)) {
                is WidgetPlan.Stacked ->
                    assertTrue(plan.cardHeight + 10f + captionHeight(plan.style, 1f) <= h + 0.01f)
                is WidgetPlan.Side -> assertTrue(captionHeight(plan.style, 1f) <= h)
                is WidgetPlan.CardOnly -> assertTrue(plan.cardHeight * 0.6f <= w + 0.01f)
            }
        }
    }
}
