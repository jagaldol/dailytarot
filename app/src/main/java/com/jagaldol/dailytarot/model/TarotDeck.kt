package com.jagaldol.dailytarot.model

import androidx.annotation.DrawableRes
import com.jagaldol.dailytarot.R

data class Card(
    val id: Int,
    val name: String,
    @param:DrawableRes val imageRes: Int,
    @param:DrawableRes val thumbnailRes: Int,
)

// Rider–Waite–Smith order: 0–21 Major Arcana, then Pentacles, Wands, Cups, Swords
val Deck = listOf(
    // Major Arcana (0–21)
    Card(0, "The Fool", R.drawable.tarot_rws_00, R.drawable.tarot_rws_thumb_00),
    Card(1, "The Magician", R.drawable.tarot_rws_01, R.drawable.tarot_rws_thumb_01),
    Card(2, "The High Priestess", R.drawable.tarot_rws_02, R.drawable.tarot_rws_thumb_02),
    Card(3, "The Empress", R.drawable.tarot_rws_03, R.drawable.tarot_rws_thumb_03),
    Card(4, "The Emperor", R.drawable.tarot_rws_04, R.drawable.tarot_rws_thumb_04),
    Card(5, "The Hierophant", R.drawable.tarot_rws_05, R.drawable.tarot_rws_thumb_05),
    Card(6, "The Lovers", R.drawable.tarot_rws_06, R.drawable.tarot_rws_thumb_06),
    Card(7, "The Chariot", R.drawable.tarot_rws_07, R.drawable.tarot_rws_thumb_07),
    Card(8, "Strength", R.drawable.tarot_rws_08, R.drawable.tarot_rws_thumb_08),
    Card(9, "The Hermit", R.drawable.tarot_rws_09, R.drawable.tarot_rws_thumb_09),
    Card(10, "Wheel of Fortune", R.drawable.tarot_rws_10, R.drawable.tarot_rws_thumb_10),
    Card(11, "Justice", R.drawable.tarot_rws_11, R.drawable.tarot_rws_thumb_11),
    Card(12, "The Hanged Man", R.drawable.tarot_rws_12, R.drawable.tarot_rws_thumb_12),
    Card(13, "Death", R.drawable.tarot_rws_13, R.drawable.tarot_rws_thumb_13),
    Card(14, "Temperance", R.drawable.tarot_rws_14, R.drawable.tarot_rws_thumb_14),
    Card(15, "The Devil", R.drawable.tarot_rws_15, R.drawable.tarot_rws_thumb_15),
    Card(16, "The Tower", R.drawable.tarot_rws_16, R.drawable.tarot_rws_thumb_16),
    Card(17, "The Star", R.drawable.tarot_rws_17, R.drawable.tarot_rws_thumb_17),
    Card(18, "The Moon", R.drawable.tarot_rws_18, R.drawable.tarot_rws_thumb_18),
    Card(19, "The Sun", R.drawable.tarot_rws_19, R.drawable.tarot_rws_thumb_19),
    Card(20, "Judgement", R.drawable.tarot_rws_20, R.drawable.tarot_rws_thumb_20),
    Card(21, "The World", R.drawable.tarot_rws_21, R.drawable.tarot_rws_thumb_21),

    // Pentacles (22–35): Ace, 2–10, Page, Knight, Queen, King
    Card(22, "Ace of Pentacles", R.drawable.tarot_rws_22, R.drawable.tarot_rws_thumb_22),
    Card(23, "Two of Pentacles", R.drawable.tarot_rws_23, R.drawable.tarot_rws_thumb_23),
    Card(24, "Three of Pentacles", R.drawable.tarot_rws_24, R.drawable.tarot_rws_thumb_24),
    Card(25, "Four of Pentacles", R.drawable.tarot_rws_25, R.drawable.tarot_rws_thumb_25),
    Card(26, "Five of Pentacles", R.drawable.tarot_rws_26, R.drawable.tarot_rws_thumb_26),
    Card(27, "Six of Pentacles", R.drawable.tarot_rws_27, R.drawable.tarot_rws_thumb_27),
    Card(28, "Seven of Pentacles", R.drawable.tarot_rws_28, R.drawable.tarot_rws_thumb_28),
    Card(29, "Eight of Pentacles", R.drawable.tarot_rws_29, R.drawable.tarot_rws_thumb_29),
    Card(30, "Nine of Pentacles", R.drawable.tarot_rws_30, R.drawable.tarot_rws_thumb_30),
    Card(31, "Ten of Pentacles", R.drawable.tarot_rws_31, R.drawable.tarot_rws_thumb_31),
    Card(32, "Page of Pentacles", R.drawable.tarot_rws_32, R.drawable.tarot_rws_thumb_32),
    Card(33, "Knight of Pentacles", R.drawable.tarot_rws_33, R.drawable.tarot_rws_thumb_33),
    Card(34, "Queen of Pentacles", R.drawable.tarot_rws_34, R.drawable.tarot_rws_thumb_34),
    Card(35, "King of Pentacles", R.drawable.tarot_rws_35, R.drawable.tarot_rws_thumb_35),

    // Wands (36–49)
    Card(36, "Ace of Wands", R.drawable.tarot_rws_36, R.drawable.tarot_rws_thumb_36),
    Card(37, "Two of Wands", R.drawable.tarot_rws_37, R.drawable.tarot_rws_thumb_37),
    Card(38, "Three of Wands", R.drawable.tarot_rws_38, R.drawable.tarot_rws_thumb_38),
    Card(39, "Four of Wands", R.drawable.tarot_rws_39, R.drawable.tarot_rws_thumb_39),
    Card(40, "Five of Wands", R.drawable.tarot_rws_40, R.drawable.tarot_rws_thumb_40),
    Card(41, "Six of Wands", R.drawable.tarot_rws_41, R.drawable.tarot_rws_thumb_41),
    Card(42, "Seven of Wands", R.drawable.tarot_rws_42, R.drawable.tarot_rws_thumb_42),
    Card(43, "Eight of Wands", R.drawable.tarot_rws_43, R.drawable.tarot_rws_thumb_43),
    Card(44, "Nine of Wands", R.drawable.tarot_rws_44, R.drawable.tarot_rws_thumb_44),
    Card(45, "Ten of Wands", R.drawable.tarot_rws_45, R.drawable.tarot_rws_thumb_45),
    Card(46, "Page of Wands", R.drawable.tarot_rws_46, R.drawable.tarot_rws_thumb_46),
    Card(47, "Knight of Wands", R.drawable.tarot_rws_47, R.drawable.tarot_rws_thumb_47),
    Card(48, "Queen of Wands", R.drawable.tarot_rws_48, R.drawable.tarot_rws_thumb_48),
    Card(49, "King of Wands", R.drawable.tarot_rws_49, R.drawable.tarot_rws_thumb_49),

    // Cups (50–63)
    Card(50, "Ace of Cups", R.drawable.tarot_rws_50, R.drawable.tarot_rws_thumb_50),
    Card(51, "Two of Cups", R.drawable.tarot_rws_51, R.drawable.tarot_rws_thumb_51),
    Card(52, "Three of Cups", R.drawable.tarot_rws_52, R.drawable.tarot_rws_thumb_52),
    Card(53, "Four of Cups", R.drawable.tarot_rws_53, R.drawable.tarot_rws_thumb_53),
    Card(54, "Five of Cups", R.drawable.tarot_rws_54, R.drawable.tarot_rws_thumb_54),
    Card(55, "Six of Cups", R.drawable.tarot_rws_55, R.drawable.tarot_rws_thumb_55),
    Card(56, "Seven of Cups", R.drawable.tarot_rws_56, R.drawable.tarot_rws_thumb_56),
    Card(57, "Eight of Cups", R.drawable.tarot_rws_57, R.drawable.tarot_rws_thumb_57),
    Card(58, "Nine of Cups", R.drawable.tarot_rws_58, R.drawable.tarot_rws_thumb_58),
    Card(59, "Ten of Cups", R.drawable.tarot_rws_59, R.drawable.tarot_rws_thumb_59),
    Card(60, "Page of Cups", R.drawable.tarot_rws_60, R.drawable.tarot_rws_thumb_60),
    Card(61, "Knight of Cups", R.drawable.tarot_rws_61, R.drawable.tarot_rws_thumb_61),
    Card(62, "Queen of Cups", R.drawable.tarot_rws_62, R.drawable.tarot_rws_thumb_62),
    Card(63, "King of Cups", R.drawable.tarot_rws_63, R.drawable.tarot_rws_thumb_63),

    // Swords (64–77)
    Card(64, "Ace of Swords", R.drawable.tarot_rws_64, R.drawable.tarot_rws_thumb_64),
    Card(65, "Two of Swords", R.drawable.tarot_rws_65, R.drawable.tarot_rws_thumb_65),
    Card(66, "Three of Swords", R.drawable.tarot_rws_66, R.drawable.tarot_rws_thumb_66),
    Card(67, "Four of Swords", R.drawable.tarot_rws_67, R.drawable.tarot_rws_thumb_67),
    Card(68, "Five of Swords", R.drawable.tarot_rws_68, R.drawable.tarot_rws_thumb_68),
    Card(69, "Six of Swords", R.drawable.tarot_rws_69, R.drawable.tarot_rws_thumb_69),
    Card(70, "Seven of Swords", R.drawable.tarot_rws_70, R.drawable.tarot_rws_thumb_70),
    Card(71, "Eight of Swords", R.drawable.tarot_rws_71, R.drawable.tarot_rws_thumb_71),
    Card(72, "Nine of Swords", R.drawable.tarot_rws_72, R.drawable.tarot_rws_thumb_72),
    Card(73, "Ten of Swords", R.drawable.tarot_rws_73, R.drawable.tarot_rws_thumb_73),
    Card(74, "Page of Swords", R.drawable.tarot_rws_74, R.drawable.tarot_rws_thumb_74),
    Card(75, "Knight of Swords", R.drawable.tarot_rws_75, R.drawable.tarot_rws_thumb_75),
    Card(76, "Queen of Swords", R.drawable.tarot_rws_76, R.drawable.tarot_rws_thumb_76),
    Card(77, "King of Swords", R.drawable.tarot_rws_77, R.drawable.tarot_rws_thumb_77)
)

data class CardSection(val title: String, val cards: List<Card>)

val DeckSections = listOf(
    CardSection("MAJOR ARCANA", Deck.subList(0, 22)),
    CardSection("WANDS", Deck.subList(36, 50)),
    CardSection("CUPS", Deck.subList(50, 64)),
    CardSection("PENTACLES", Deck.subList(22, 36)),
    CardSection("SWORDS", Deck.subList(64, 78)),
)

private val majorNumbers = listOf(
    "0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X",
    "XI", "XII", "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX", "XXI",
)

fun displayLabel(id: Int): String {
    val card = Deck.getOrNull(id) ?: return ""
    return if (id <= 21) "${majorNumbers[id]}. ${card.name}" else card.name
}
