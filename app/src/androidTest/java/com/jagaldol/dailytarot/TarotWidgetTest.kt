package com.jagaldol.dailytarot

import android.graphics.Bitmap
import android.graphics.Canvas
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
import com.jagaldol.dailytarot.data.FortuneCatalog
import com.jagaldol.dailytarot.data.withAppLanguage
import com.jagaldol.dailytarot.widget.DailyTarotWidget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class TarotWidgetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as TarotApplication

    @Before
    fun standaloneStar() = runBlocking {
        app.sync.disconnect()
        app.repository.drawToday()
        assertTrue(app.repository.selectManually(17, true))
    }

    @Test
    fun cardFillsSmallWidgetsAndRoomierOnesAddOneLineOfFortune() = runBlocking {
        val headline = FortuneCatalog.load(context.withAppLanguage()).entry(17, true).fortuneText
        val sizes = mapOf(
            "small" to (DpSize(150.dp, 150.dp) to false),
            "tall" to (DpSize(170.dp, 380.dp) to true),
            "wide" to (DpSize(330.dp, 160.dp) to true),
            "full" to (DpSize(360.dp, 560.dp) to true),
            "tight" to (DpSize(172.dp, 212.dp) to true),
            "tiny" to (DpSize(120.dp, 120.dp) to false),
        )
        for ((name, spec) in sizes) {
            val (size, showsFortune) = spec
            val remoteViews = DailyTarotWidget().compose(context, size = size)
            instrumentation.runOnMainSync {
                val view = remoteViews.apply(context, FrameLayout(context))
                val image = descendants(view).filterIsInstance<ImageView>().first { it.contentDescription != null }
                assertEquals(description(17, reversed = true), image.contentDescription)
                assertTrue(image.drawable != null)
                val texts = descendants(view).filterIsInstance<TextView>().map { it.text.toString().replace("\u2060", "") }.toList()
                assertEquals(name, showsFortune, headline in texts)
                save(view, size, name)
            }
        }
    }

    /** Leaves PNGs for visual review: adb pull /sdcard/Android/data/<pkg>/files/widgets */
    private fun save(view: View, size: DpSize, name: String) {
        val density = context.resources.displayMetrics.density
        val width = (size.width.value * density).roundToInt()
        val height = (size.height.value * density).roundToInt()
        val frame = FrameLayout(context).apply { addView(view, FrameLayout.LayoutParams(width, height)) }
        frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(bitmap))
        val dir = File(context.getExternalFilesDir(null), "widgets").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
        }
    }
}
