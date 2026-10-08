package com.jagaldol.dailytarot.data.lifebase

import com.jagaldol.dailytarot.data.lifebase.JournalTarotParser.Incomplete
import com.jagaldol.dailytarot.data.lifebase.JournalTarotParser.Invalid
import com.jagaldol.dailytarot.data.lifebase.JournalTarotParser.NoCard
import com.jagaldol.dailytarot.data.lifebase.JournalTarotParser.Parsed
import com.jagaldol.dailytarot.model.ContentStatus
import com.jagaldol.dailytarot.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures follow the real note layouts with invented diary text. */
class JournalTarotParserTest {
    private val ids = Deck.associate { it.name to it.id }

    private fun note(
        card: String = "Page of Cups",
        reverse: String = "false",
        fortune: String = CURRENT_FORTUNE,
        extraFrontmatter: String = "",
        before: String = "",
    ) = """
        |---
        |tags: [일간]
        |journal_wrapup: false
        |tarot-card: $card
        |tarot-reverse: $reverse
        |$extraFrontmatter---
        |2030년 1월 2일(수)
        |```dataviewjs
        |const current = dv.date("2030-01-02");
        |```
        |
        |## 일기
        |
        |비밀 일기 문장
        |$before
        |## 일정
        |
        |- 09:00 - 10:00 회의
        |
        |## 오늘의 운세
        |
        |$fortune
        |
        |## 메모
        |
        |- 개인 메모
        |""".trimMargin()

    private fun parse(text: String) = JournalTarotParser.parse(text, ids)

    @Test
    fun currentFormatKeepsHeadlineKeywordsAndBodyOnly() {
        val parsed = parse(note()) as Parsed
        assertEquals(60, parsed.cardId)
        assertFalse(parsed.reversed)
        assertEquals("작은 진심이 먼저 살아납니다", parsed.headline)
        assertEquals("감수성, 새 마음, 공상", parsed.keywordsText)
        assertEquals("첫 문단은 컵 페이지 이야기예요.\n\n둘째 문단은 그대로 남아요.", parsed.body)
        assertEquals(ContentStatus.COMPLETE, parsed.status)
        assertTrue(parsed.fortuneRaw.startsWith("![컵 페이지"))
        for (privateText in listOf("비밀 일기", "회의", "개인 메모", "dataviewjs")) {
            assertFalse(privateText, parsed.fortuneRaw.contains(privateText))
            assertFalse(privateText, parsed.body!!.contains(privateText))
        }
    }

    @Test
    fun legacyKeywordOnlyFormatIsAcceptedWithoutInventingText() {
        val fortune = """
            |![ace-of-cups|360](https://example.com/ace-of-cups.webp)
            |
            |> [!quote] [[Ace of Cups|컵 에이스 (Ace of Cups)]] · 정방향
            |>
            |> 키워드 — 감정, 마음, 자연스러운 흐름, 충만
            """.trimMargin()
        val parsed = parse(note(card = "Ace of Cups", fortune = fortune)) as Parsed
        assertEquals(50, parsed.cardId)
        assertNull(parsed.headline)
        assertNull(parsed.body)
        assertEquals("감정, 마음, 자연스러운 흐름, 충만", parsed.keywordsText)
        assertEquals(ContentStatus.KEYWORDS_ONLY, parsed.status)
    }

    @Test
    fun bomCrlfAndQuotedScalarsAreRead() {
        val text = "﻿" + note(card = "\"The Star\"", reverse = "'True'", fortune = REVERSED_STAR)
            .replace("\n", "\r\n")
        val parsed = parse(text) as Parsed
        assertEquals(17, parsed.cardId)
        assertTrue(parsed.reversed)
        assertEquals("별빛이 늦게 도착해요", parsed.headline)
    }

    @Test
    fun notesWithoutACardAreNotDrawnYet() {
        assertEquals(NoCard, parse(note(card = "")))
        assertEquals(NoCard, parse("## 일기\n\n오늘"))
    }

    @Test
    fun writingInProgressIsIncomplete() {
        assertTrue(parse(note(fortune = "")) is Incomplete)
        val headlineOnly = """
            |> [!quote] [[Page of Cups|컵 페이지 (Page of Cups)]] · 정방향
            |> **작은 진심이 먼저 살아납니다**
            |> 키워드: 감수성
            """.trimMargin()
        assertTrue(parse(note(fortune = headlineOnly)) is Incomplete)
        val noSection = note().replace("## 오늘의 운세", "## 다른 제목")
        assertTrue(parse(noSection) is Incomplete)
    }

    @Test
    fun contradictoryOrUnsupportedNotesAreRejected() {
        assertTrue(parse(note(card = "The Unknown")) is Invalid)
        assertTrue(parse(note(reverse = "maybe")) is Invalid)
        assertTrue(parse(note(card = "Page of Cups # comment")) is Invalid)
        assertTrue(parse(note(extraFrontmatter = "tarot-card: The Sun\n")) is Invalid)
        assertTrue(parse(note(reverse = "true")) is Invalid) // body says 정방향
        assertTrue(parse(note(card = "The Sun")) is Invalid) // body links Page of Cups
        assertTrue(parse(note(before = "## 오늘의 운세\n\n> 중복\n")) is Invalid)
        assertTrue(parse("---\ntarot-card: The Sun\n") is Invalid)
    }

    @Test
    fun headingsInsideCodeFencesAreIgnored() {
        val fenced = note(before = "```\n## 오늘의 운세\n## 가짜\n```\n")
        val parsed = parse(fenced) as Parsed
        assertEquals("작은 진심이 먼저 살아납니다", parsed.headline)
    }

    @Test
    fun sectionStopsAtTheNextHeading() {
        val parsed = parse(note()) as Parsed
        assertFalse(parsed.fortuneRaw.contains("## 메모"))
        assertTrue(parsed.fortuneRaw.endsWith("둘째 문단은 그대로 남아요."))
    }

    @Test
    fun hashTracksTarotContentOnly() {
        val first = parse(note()) as Parsed
        val diaryEdited = parse(note().replace("비밀 일기 문장", "고친 일기")) as Parsed
        val fortuneEdited = parse(note().replace("둘째 문단은", "고친 둘째 문단은")) as Parsed
        assertEquals(first.hash, diaryEdited.hash)
        assertNotEquals(first.hash, fortuneEdited.hash)
    }

    @Test
    fun displayTextDropsObsidianMarkup() {
        assertEquals(
            "컵 페이지와 링크 그리고 강조",
            JournalTarotParser.display("[[Page of Cups|컵 페이지]]와 [링크](https://x.y) 그리고 **강조**"),
        )
    }

    companion object {
        private val CURRENT_FORTUNE = """
            |![컵 페이지 · 정방향|360](https://example.com/page-of-cups.webp)
            |
            |> [!quote] [[Page of Cups|컵 페이지 (Page of Cups)]] · 정방향
            |> **작은 진심이 먼저 살아납니다**
            |> 키워드: 감수성, 새 마음, 공상
            |>
            |> 첫 문단은 [[Page of Cups|컵 페이지]] 이야기예요.
            |>
            |> 둘째 문단은 그대로 남아요.
            """.trimMargin()

        private val REVERSED_STAR = """
            |> [!quote] [[The Star|별 (The Star)]] · 역방향
            |> **별빛이 늦게 도착해요**
            |> 키워드: 희망
            |>
            |> 본문
            """.trimMargin()
    }
}
