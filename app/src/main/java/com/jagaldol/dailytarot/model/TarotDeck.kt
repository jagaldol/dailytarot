package com.jagaldol.dailytarot.model

import android.content.Context
import com.jagaldol.dailytarot.R

data class Card(val id: Int, val name: String)

// Rider–Waite–Smith order: 0–21 Major Arcana, then Pentacles, Wands, Cups, Swords
val Deck = listOf(
    // Major Arcana (0–21)
    Card(0, "The Fool"),
    Card(1, "The Magician"),
    Card(2, "The High Priestess"),
    Card(3, "The Empress"),
    Card(4, "The Emperor"),
    Card(5, "The Hierophant"),
    Card(6, "The Lovers"),
    Card(7, "The Chariot"),
    Card(8, "Strength"),
    Card(9, "The Hermit"),
    Card(10, "Wheel of Fortune"),
    Card(11, "Justice"),
    Card(12, "The Hanged Man"),
    Card(13, "Death"),
    Card(14, "Temperance"),
    Card(15, "The Devil"),
    Card(16, "The Tower"),
    Card(17, "The Star"),
    Card(18, "The Moon"),
    Card(19, "The Sun"),
    Card(20, "Judgement"),
    Card(21, "The World"),

    // Pentacles (22–35): Ace, 2–10, Page, Knight, Queen, King
    Card(22, "Ace of Pentacles"),
    Card(23, "Two of Pentacles"),
    Card(24, "Three of Pentacles"),
    Card(25, "Four of Pentacles"),
    Card(26, "Five of Pentacles"),
    Card(27, "Six of Pentacles"),
    Card(28, "Seven of Pentacles"),
    Card(29, "Eight of Pentacles"),
    Card(30, "Nine of Pentacles"),
    Card(31, "Ten of Pentacles"),
    Card(32, "Page of Pentacles"),
    Card(33, "Knight of Pentacles"),
    Card(34, "Queen of Pentacles"),
    Card(35, "King of Pentacles"),

    // Wands (36–49)
    Card(36, "Ace of Wands"),
    Card(37, "Two of Wands"),
    Card(38, "Three of Wands"),
    Card(39, "Four of Wands"),
    Card(40, "Five of Wands"),
    Card(41, "Six of Wands"),
    Card(42, "Seven of Wands"),
    Card(43, "Eight of Wands"),
    Card(44, "Nine of Wands"),
    Card(45, "Ten of Wands"),
    Card(46, "Page of Wands"),
    Card(47, "Knight of Wands"),
    Card(48, "Queen of Wands"),
    Card(49, "King of Wands"),

    // Cups (50–63)
    Card(50, "Ace of Cups"),
    Card(51, "Two of Cups"),
    Card(52, "Three of Cups"),
    Card(53, "Four of Cups"),
    Card(54, "Five of Cups"),
    Card(55, "Six of Cups"),
    Card(56, "Seven of Cups"),
    Card(57, "Eight of Cups"),
    Card(58, "Nine of Cups"),
    Card(59, "Ten of Cups"),
    Card(60, "Page of Cups"),
    Card(61, "Knight of Cups"),
    Card(62, "Queen of Cups"),
    Card(63, "King of Cups"),

    // Swords (64–77)
    Card(64, "Ace of Swords"),
    Card(65, "Two of Swords"),
    Card(66, "Three of Swords"),
    Card(67, "Four of Swords"),
    Card(68, "Five of Swords"),
    Card(69, "Six of Swords"),
    Card(70, "Seven of Swords"),
    Card(71, "Eight of Swords"),
    Card(72, "Nine of Swords"),
    Card(73, "Ten of Swords"),
    Card(74, "Page of Swords"),
    Card(75, "Knight of Swords"),
    Card(76, "Queen of Swords"),
    Card(77, "King of Swords")
)

// Resolve image resource by sequential index: tarot_rws_00..tarot_rws_77 in drawable(-nodpi)
fun imageResFor(context: Context, id: Int): Int {
    val safeId = if (id in 0..77) id else -1
    if (safeId == -1) return R.mipmap.ic_launcher
    val name = String.format("tarot_rws_%02d", safeId)
    val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
    return if (resId != 0) resId else R.mipmap.ic_launcher
}

// Thumbnail resource for grid (smaller WebP for smooth scrolling)
fun thumbResFor(context: Context, id: Int): Int {
    val safeId = if (id in 0..77) id else -1
    if (safeId == -1) return imageResFor(context, id)
    val name = String.format("tarot_rws_thumb_%02d", safeId)
    val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
    return if (resId != 0) resId else imageResFor(context, id)
}

private fun roman(num: Int): String {
    // Supports 1..3999; we only need up to 21
    if (num <= 0) return ""
    val thousands = arrayOf("", "M", "MM", "MMM")
    val hundreds = arrayOf("", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM")
    val tens = arrayOf("", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC")
    val ones = arrayOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")
    return buildString {
        append(thousands[(num / 1000) % 10])
        append(hundreds[(num / 100) % 10])
        append(tens[(num / 10) % 10])
        append(ones[num % 10])
    }
}

fun displayLabel(id: Int): String {
    return when (id) {
        in 1..21 -> {
            val n = roman(id)
            "$n. ${Deck.first { it.id == id }.name.uppercase()}"
        }
        0 -> "0. ${Deck.first { it.id == 0 }.name.uppercase()}"
        in 22..35 -> minorLabelWords(id, suit = "Pentacles", base = 22).uppercase()
        in 36..49 -> minorLabelWords(id, suit = "Wands", base = 36).uppercase()
        in 50..63 -> minorLabelWords(id, suit = "Cups", base = 50).uppercase()
        in 64..77 -> minorLabelWords(id, suit = "Swords", base = 64).uppercase()
        else -> Deck.firstOrNull { it.id == id }?.name?.uppercase() ?: ""
    }
}

private fun minorLabelWords(id: Int, suit: String, base: Int): String {
    val pos = id - base // 0..13
    return when (pos) {
        0 -> "Ace of $suit"
        in 1..9 -> "${numberWord(pos + 1)} of $suit" // 2..10
        10 -> "Page of $suit"
        11 -> "Knight of $suit"
        12 -> "Queen of $suit"
        13 -> "King of $suit"
        else -> ""
    }
}

private fun numberWord(n: Int): String = when (n) {
    1 -> "One" // not used (Ace covers 1)
    2 -> "Two"
    3 -> "Three"
    4 -> "Four"
    5 -> "Five"
    6 -> "Six"
    7 -> "Seven"
    8 -> "Eight"
    9 -> "Nine"
    10 -> "Ten"
    else -> n.toString()
}
