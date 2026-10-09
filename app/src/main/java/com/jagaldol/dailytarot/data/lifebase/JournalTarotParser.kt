package com.jagaldol.dailytarot.data.lifebase

import com.jagaldol.dailytarot.model.ContentStatus
import java.security.MessageDigest
import java.util.Locale

/**
 * Reads only the tarot fields of a Lifebase daily note: the `tarot-card` / `tarot-reverse`
 * frontmatter keys and the reading section (`## 오늘의 운세` in Korean vaults, `## Today's Tarot`
 * in English ones, or a title the user set). Diary, schedule and memo text is never returned.
 * Unknown shapes are rejected instead of guessed.
 */
object JournalTarotParser {
    const val MAX_NOTE_BYTES = 1 shl 20
    const val MAX_SECTION_CHARS = 64 * 1024
    const val MAX_TITLE_CHARS = 80

    /** The section titles Lifebase writes, Korean first. */
    val DEFAULT_TITLES = listOf("오늘의 운세", "Today's Tarot")

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
    private val sectionHeading = Regex("""^ {0,3}## +(.*\S)[ \t]*$""")
    private val closingSpaced = Regex("""[ \t]+#+$""")
    private val closingTight = Regex("""#+$""")
    private val majorHeading = Regex("""^ {0,3}#{1,2}(?:[ \t]|$)""")
    private val fence = Regex("""^ {0,3}(`{3,}|~{3,})""")
    private val calloutStart = Regex("""^>\s*\[!quote][-+]?\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val keywordLine = Regex("""^(?:키워드\s*(?::|—|–|-)|keywords?\s*:)\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val reversedWord = Regex("""\breversed\b""", RegexOption.IGNORE_CASE)
    private val uprightWord = Regex("""\bupright\b""", RegexOption.IGNORE_CASE)
    private val boldLine = Regex("""^\*\*(.+)\*\*$""")
    private val wikiLink = Regex("""\[\[([^\]|]+)(?:\|([^\]]+))?]]""")
    private val markdownLink = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val htmlTag = Regex("""<[^>]+>""")

    /**
     * The title a user typed in settings, cleaned the way a heading is written: surrounding
     * spaces and leading `#` marks dropped. Null when nothing usable is left or it is too long.
     */
    fun headingTitle(input: String): String? {
        if ('\n' in input || '\r' in input) return null
        val title = input.trim().trimStart('#').trim().replace(closingSpaced, "").trim().replace(Regex("""\s+"""), " ")
        return title.takeIf {
            it.isNotEmpty() && it.length <= MAX_TITLE_CHARS && normalized(it) !in RESERVED_TITLES
        }
    }

    /** Whether [input] names one of Lifebase's own daily sections, which [headingTitle] refuses. */
    fun isReservedTitle(input: String): Boolean =
        normalized(input.trim().trimStart('#').trim().replace(closingSpaced, "")) in RESERVED_TITLES

    /**
     * Lifebase's own daily sections. Reading one of them as the tarot section would store diary,
     * schedule or memo text, so they cannot be chosen as the heading.
     */
    private val RESERVED_TITLES = listOf("일기", "일정", "할 일", "메모", "Diary", "Schedule", "To do", "Notes")
        .map(::normalized).toSet()

    /** A custom title is looked for first; the Lifebase titles still cover notes written before it. */
    fun titles(custom: String?): List<String> = (listOfNotNull(custom) + DEFAULT_TITLES).distinctBy(::normalized)

    fun parse(note: String, cardIds: Map<String, Int>, titles: List<String> = DEFAULT_TITLES): Result {
        val lines = note.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        if (lines.firstOrNull()?.trimEnd() != "---") return NoCard
        val end = (1 until lines.size).firstOrNull { lines[it].trimEnd() == "---" }
            ?: return Invalid("Frontmatter is not closed")

        val values = HashMap<String, String>()
        for (line in lines.subList(1, end)) {
            val (key, raw) = frontmatterKey.matchEntire(line)?.destructured ?: continue
            if (key != "tarot-card" && key != "tarot-reverse") continue
            if (key in values) return Invalid("$key appears twice")
            values[key] = scalar(raw) ?: return Invalid("$key is unreadable")
        }
        val cardName = values["tarot-card"]?.takeIf { it.isNotEmpty() } ?: return NoCard
        val cardId = cardIds[cardName] ?: return Invalid("Unknown card: $cardName")
        val reversed = when (values["tarot-reverse"]?.lowercase()) {
            "true" -> true
            "false" -> false
            null -> return Invalid("tarot-reverse is missing")
            else -> return Invalid("tarot-reverse is not true or false")
        }

        val section = section(lines.subList(end + 1, lines.size), titles) ?: return Incomplete("No reading section yet")
        if (section is SectionError) return Invalid(section.reason)
        val raw = (section as SectionText).text
        if (raw.isBlank()) return Incomplete("The reading section is empty")
        if (raw.length > MAX_SECTION_CHARS) return Invalid("The reading section is too long")

        val sectionLines = raw.split('\n')
        val start = sectionLines.indexOfFirst { calloutStart.matches(it.trimStart()) }
        if (start < 0) return Incomplete("No reading callout yet")
        val title = calloutStart.matchEntire(sectionLines[start].trimStart())!!.groupValues[1]
        wikiLink.find(title)?.groupValues?.get(1)?.trim()?.let { target ->
            if (cardIds[target] != cardId) return Invalid("The callout card differs from the frontmatter")
        }
        // Lifebase ends the title with "· 정방향" or "· Upright"; only that part names the orientation.
        val orientation = if ('·' in title) title.substringAfterLast('·') else title
        val titleReversed = when {
            "역방향" in orientation || reversedWord.containsMatchIn(orientation) -> true
            "정방향" in orientation || uprightWord.containsMatchIn(orientation) -> false
            else -> null
        }
        if (titleReversed != null && titleReversed != reversed) return Invalid("The callout orientation differs from the frontmatter")

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
            headline != null -> return Incomplete("The reading has no body yet")
            keywordsText != null -> ContentStatus.KEYWORDS_ONLY
            else -> return Incomplete("The reading has no content yet")
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

    /** The first of [titles] present in the note wins; each title must appear at most once. */
    private fun section(lines: List<String>, titles: List<String>): Section? {
        for (title in titles) section(lines, normalized(title))?.let { return it }
        return null
    }

    private fun section(lines: List<String>, title: String): Section? {
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
            if (sectionHeading.matchEntire(line)?.groupValues?.get(1)?.let { headingMatches(it, title) } == true) {
                if (start >= 0) return SectionError("The reading heading appears twice")
                start = index + 1
            } else if (start >= 0 && end == lines.size && majorHeading.containsMatchIn(line)) {
                end = index
            }
        }
        if (start < 0) return null
        return SectionText(lines.subList(start, end).joinToString("\n").trim('\n', ' ', '\t'))
    }

    /**
     * `## Title ##` closes with hashes after a space; Lifebase notes written before 2.2 may also
     * have `## 오늘의 운세##`. A title that itself ends in `#` (such as "C#") still matches as written.
     */
    private fun headingMatches(text: String, title: String): Boolean =
        normalized(text) == title || normalized(text.replace(closingSpaced, "")) == title ||
            normalized(text.replace(closingTight, "")) == title

    /** Headings compare without case, repeated spaces or typographic apostrophes. */
    private fun normalized(title: String): String =
        title.trim().replace('\u2019', '\'').replace('\u2018', '\'').replace(Regex("""\s+"""), " ")
            .lowercase(Locale.ROOT)

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
