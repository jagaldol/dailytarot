package com.jagaldol.dailytarot.model

import com.jagaldol.dailytarot.R

data class Card(val id: Int, val name: String)

val Deck = listOf(
    Card(0, "The Fool"),
    Card(1, "The Magician"),
    Card(2, "The High Priestess"),
    Card(3, "The Empress"),
    Card(4, "The Emperor")
)

// 이미지 리소스 매핑 (id 순서와 맞추기)
val CardImageRes = intArrayOf(
    R.drawable.tarot_mj_00_the_fool,
    R.drawable.tarot_mj_01_the_magician,
    R.drawable.tarot_mj_02_the_high_priestess,
    R.drawable.tarot_mj_03_the_empress,
    R.drawable.tarot_mj_04_the_emperor
    // … 계속 78장
)

// 안전 접근: 없으면 앱 아이콘으로 대체
fun imageResFor(id: Int): Int =
    CardImageRes.getOrNull(id) ?: R.mipmap.ic_launcher