package com.jagaldol.dailytarot

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jagaldol.dailytarot.data.TarotStore
import com.jagaldol.dailytarot.model.TodaySelection
import com.jagaldol.dailytarot.widget.DailyTarotWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class TarotWidgetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun newlyCreatedWidgetsReadExistingSelectionWithoutAnAppInteraction() = runBlocking {
        val store = TarotStore(context)
        val original = store.load()
        try {
            store.save(TodaySelection(17, true))
            for (size in listOf(DpSize(180.dp, 180.dp), DpSize(300.dp, 300.dp))) {
                val remoteViews = DailyTarotWidget().compose(context, size = size)
                instrumentation.runOnMainSync {
                    val view = remoteViews.apply(context, FrameLayout(context))
                    val image = descendants(view).filterIsInstance<ImageView>().single { it.contentDescription != null }
                    assertEquals("The Star, Reversed", image.contentDescription)
                    assertTrue(image.drawable != null)
                }
            }
            store.save(TodaySelection(21, false))
            val updated = DailyTarotWidget().compose(context, size = DpSize(180.dp, 180.dp))
            instrumentation.runOnMainSync {
                val image = descendants(updated.apply(context, FrameLayout(context)))
                    .filterIsInstance<ImageView>().single { it.contentDescription != null }
                assertEquals("The World, Upright", image.contentDescription)
            }
        } finally {
            store.save(original)
        }
    }

    @Test
    fun emptyWidgetExplainsHowToChooseACard() = runBlocking {
        val store = TarotStore(context)
        val original = store.load()
        try {
            store.save(TodaySelection())
            val remoteViews = DailyTarotWidget().compose(context, size = DpSize(180.dp, 180.dp))
            instrumentation.runOnMainSync {
                val texts = descendants(remoteViews.apply(context, FrameLayout(context)))
                    .filterIsInstance<TextView>().map { it.text.toString() }.toList()
                assertTrue(texts.contains(context.getString(R.string.widget_empty)))
            }
        } finally {
            store.save(original)
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
        }
    }
}
