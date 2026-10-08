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
