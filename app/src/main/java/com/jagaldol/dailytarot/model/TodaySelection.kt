package com.jagaldol.dailytarot.model

data class TodaySelection(val cardId: Int? = null, val reversed: Boolean = false) {
    init {
        require(cardId == null || cardId in 0..77)
    }
}
