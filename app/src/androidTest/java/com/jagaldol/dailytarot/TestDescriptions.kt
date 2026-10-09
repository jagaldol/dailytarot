package com.jagaldol.dailytarot

import androidx.test.platform.app.InstrumentationRegistry
import com.jagaldol.dailytarot.data.FortuneCatalog
import com.jagaldol.dailytarot.data.withAppLanguage
import com.jagaldol.dailytarot.ui.cardDescription

/** "별 (The Star), 역방향" on a Korean device, "The Star, Reversed" in English. */
fun description(cardId: Int, reversed: Boolean): String {
    // The language the widget renders in: the per-app choice when set, otherwise the device's.
    val context = InstrumentationRegistry.getInstrumentation().targetContext.withAppLanguage()
    val orientation = context.getString(if (reversed) R.string.reversed else R.string.upright)
    return "${cardDescription(cardId, FortuneCatalog.load(context).name(cardId))}, $orientation"
}
