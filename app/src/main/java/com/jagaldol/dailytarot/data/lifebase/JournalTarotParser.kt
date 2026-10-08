package com.jagaldol.dailytarot.data.lifebase

import com.jagaldol.dailytarot.model.ContentStatus
import java.security.MessageDigest

/**
 * Reads only the tarot fields of a Lifebase daily note: the `tarot-card` / `tarot-reverse`
 * frontmatter keys and the `## 오늘의 운세` section. Diary, schedule and memo text is never
 * returned. Unknown shapes are rejected instead of guessed.
 */
object JournalTarotParser {
    const val MAX_NOTE_BYTES = 1 shl 20
    const val MAX_SECTION_CHARS = 64 * 1024
    const val SECTION_TITLE = "오늘의 운세"

    sealed interface Result

    data class Parsed(
        val cardId: Int,
        val reversed: Boolean,
        val headline: String?,
        val keywordsText: String?,
        val body: String?,
        val fortuneRaw: String,
        val status: ContentStatus,
        val hash: String,
    ) : Result

    /** The note exists but no card has been drawn into it yet. */
    data object NoCard : Result

    /** A card is set but the fortune is still being written. */
    data class Incomplete(val reason: String) : Result

    /** The note contradicts itself or uses an unsupported format. */
    data class Invalid(val reason: String) : Result

    private val frontmatterKey = Regex("""^([A-Za-z0-9_-]+)\s*:(.*)$""")
    private val sectionHeading = Regex("""^ {0,3}## +${SECTION_TITLE}[ \t]*#*[ \t]*$""")
    private val majorHeading = Regex("""^ {0,3}#{1,2}(?:[ \t]|$)""")
    private val fence = Regex("""^ {0,3}(`{3,}|~{3,})""")
    private val calloutStart = Regex("""^>\s*\[!quote][-+]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val keywordLine = Regex("""^키워드\s*(?::|—|–|-)\s*(.*)$""")
    private val boldLine = Regex("""^\*\*(.+)\*\*$""")
    private val wikiLink = Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?]]""")
    private val markdownLink = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val htmlTag = Regex("""<[^>]+>""")

    fun parse(note: String, cardIds: Map<String, Int>): Result {
        val lines = note.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        if (lines.firstOrNull()?.trimEnd() != "---") return NoCard
        val end = (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" }
            ?: return Invalid("frontmatter가 닫히지 않았어요")

        val values = HashMap<String, String>()
        for (line in lines.subList(1, end)) {
            val (key, raw) = frontmatterKey.matchEntire(line)?.destructured ?: continue
            if (key != "tarot-card" && key != "tarot-reverse") continue
            if (key in values) return Invalid("$key 값이 두 번 적혀 있어요")
            values[key] = scalar(raw) ?: return Invalid("$key 값을 읽을 수 없어요")
        }
        val cardName = values["tarot-card"]?.takeIf { it.isNotEmpty() } ?: return NoCard
        val cardId = cardIds[cardName] ?: return Invalid("알 수 없는 카드: $cardName")
        val reversed = when (values["tarot-reverse"]?.lowercase()) {
            "true" -> true
            "false" -> false
            null -> return Invalid("tarot-reverse 값이 없어요")
            else -> return Invalid("tarot-reverse 값이 올바르지 않아요")
        }

        val section = section(lines.subList(end + 1, lines.size)) ?: return Incomplete("운세 섹션이 아직 없어요")
        if (section is SectionError) return Invalid(section.reason)
        val raw = (section as SectionText).text
        if (raw.isBlank()) return Incomplete("운세가 아직 비어 있어요")
        if (raw.length > MAX_SECTION_CHARS) return Invalid("운세 섹션이 너무 길어요")

        val sectionLines = raw.split('\n')
        val start = sectionLines.indexOfFirst { calloutStart.matches(it.trimStart()) }
        if (start < 0) return Incomplete("운세 콜아웃이 아직 없어요")
        val title = calloutStart.matchEntire(sectionLines[start].trimStart())!!.groupValues[1]
        wikiLink.find(title)?.groupValues?.get(1)?.trim()?.let { target ->
            if (cardIds[target] != cardId) return Invalid("본문 카드가 frontmatter와 달라요")
        }
        val titleReversed = when {
            "역방향" in title -> true
            "정방향" in title -> false
            else -> null
        }
        if (titleReversed != null && titleReversed != reversed) return Invalid("본문 방향이 frontmatter와 달라요")

        var headline: String? = null
        var keywords: String? = null
        val bodyLines = ArrayList<String>()
        for (line in sectionLines.drop(start + 1)) {
            if (!line.startsWith(">")) break
            val content = line.removePrefix(">").removePrefix(" ").trimEnd()
            val keywordMatch = keywordLine.matchEntire(content)
            val boldMatch = boldLine.matchEntire(content)
            when {
                keywordMatch != null && keywords == null -> keywords = keywordMatch.groupValues[1].trim()
                boldMatch != null && headline == null && keywords == null && bodyLines.isEmpty() ->
                    headline = display(boldMatch.groupValues[1])
                content.isEmpty() -> if (bodyLines.isNotEmpty()) bodyLines += ""
                else -> bodyLines += display(content)
            }
        }
        val body = bodyLines.joinToString("\n").trim().takeIf { it.isNotEmpty() }
        val keywordsText = keywords?.let(::display)?.takeIf { it.isNotEmpty() }

        val status = when {
            body != null -> ContentStatus.COMPLETE
            headline != null -> return Incomplete("운세 본문이 아직 없어요")
            keywordsText != null -> ContentStatus.KEYWORDS_ONLY
            else -> return Incomplete("운세 내용이 아직 없어요")
        }
        return Parsed(
            cardId = cardId,
            reversed = reversed,
            headline = headline,
            keywordsText = keywordsText,
            body = body,
            fortuneRaw = raw,
            status = status,
            hash = sha256("$cardId|$reversed|$raw"),
        )
    }

    private sealed interface Section
    private data class SectionText(val text: String) : Section
    private data class SectionError(val reason: String) : Section

    private fun section(lines: List<String>): Section? {
        var fenceMarker: String? = null
        var start = -1
        var end = lines.size
        for ((index, line) in lines.withIndex()) {
            val fenceMatch = fence.find(line)
            if (fenceMarker != null) {
                if (fenceMatch != null && fenceMatch.groupValues[1].startsWith(fenceMarker)) fenceMarker = null
                continue
            }
            if (fenceMatch != null) {
                fenceMarker = fenceMatch.groupValues[1].take(3)
                continue
            }
            if (sectionHeading.matches(line)) {
                if (start >= 0) return SectionError("운세 제목이 두 번 있어요")
                start = index + 1
            } else if (start >= 0 && end == lines.size && majorHeading.containsMatchIn(line)) {
                end = index
            }
        }
        if (start < 0) return null
        return SectionText(lines.subList(start, end).joinToString("\n").trim('\n', ' ', '\t'))
    }

    /** Supports plain or quoted scalars; YAML comments and other extensions are rejected. */
    private fun scalar(raw: String): String? {
        val value = raw.trim()
        if (value.length >= 2 && (value[0] == '"' || value[0] == '\'') && value.last() == value[0]) {
            return value.substring(1, value.length - 1).trim()
        }
        if (value.startsWith('"') || value.startsWith('\'') || " #" in value || value.startsWith("#")) return null
        return value
    }

    /** Obsidian markup becomes plain text for display; words and line breaks stay as written. */
    fun display(text: String): String = text
        .replace(wikiLink) { (it.groupValues[2].ifEmpty { it.groupValues[1] }).trim() }
        .replace(markdownLink) { it.groupValues[1] }
        .replace(htmlTag, "")
        .replace("**", "")
        .replace("__", "")
        .trim()

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
