package com.jagaldol.dailytarot.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.jagaldol.dailytarot.MainActivity
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.TarotApplication
import com.jagaldol.dailytarot.data.AppSettings
import com.jagaldol.dailytarot.data.CardImages
import com.jagaldol.dailytarot.model.DailyReading
import com.jagaldol.dailytarot.model.Day
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.model.weekdayKo
import com.jagaldol.dailytarot.ui.minutesLabel
import com.jagaldol.dailytarot.work.RefreshScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The card is the widget: a transparent background, the card as large as the cell allows,
 * and one quiet caption (date, name, today's line) only when there is room for it.
 */
class DailyTarotWidget : GlanceAppWidget() {
    // Readings are application data, not per-widget state: every widget shows the same date.
    override val stateDefinition = null

    // Exact sizes let the card fill the cell and decide whether a caption fits.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TarotApplication
        // Draws only when automatic drawing is on and its time has passed.
        val initial = try {
            app.repository.refreshToday()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val initialImage = initial?.reading to initial?.reading?.let { render(context, it) }
        // What a face-down card waits for: the journal, the automatic draw, or the user.
        val waitingFor: (AppSettings) -> String = { settings ->
            when {
                settings.connection != null -> context.getString(R.string.widget_waiting_lifebase)
                settings.autoDraw -> context.getString(R.string.widget_auto_draw_at, minutesLabel(settings.autoDrawMinutes))
                else -> context.getString(R.string.widget_draw_prompt)
            }
        }
        val initialWaiting = waitingFor(app.settings.state.first())
        val waitingFlow = app.settings.state.map(waitingFor)
        val initialDraw = app.settings.connection() == null
        val drawFlow = app.settings.state.map { it.connection == null }
        provideContent {
            val view by app.repository.todayView.collectAsState(initial)
            val waiting by waitingFlow.collectAsState(initialWaiting)
            val reading = view?.reading
            val image by produceState(initialImage, reading) {
                value = reading to reading?.let { render(context, it) }
            }
            val (shown, bitmap) = image
            // Only a standalone, face-down widget draws on tap; with Lifebase it just opens the app.
            val drawOnTap by drawFlow.collectAsState(initialDraw)
            WidgetBody(context, app, view?.day, shown, bitmap, waiting, drawOnTap && view?.awaitingDraw == true)
        }
    }

    private suspend fun render(context: Context, reading: DailyReading): Bitmap =
        CardImages.widget(context.resources, Deck[reading.cardId].imageRes, reading.reversed)
}

private const val CARD_RATIO = 0.6f
private val Padding = 6.dp
private val Gap = 10.dp

private val text = ColorProvider(day = Color(0xFF1E1C29), night = Color(0xFFEEE9E0))
private val accent = ColorProvider(day = Color(0xFF8C6527), night = Color(0xFFDCC08A))

@Composable
private fun WidgetBody(
    context: Context,
    app: TarotApplication,
    today: Day?,
    reading: DailyReading?,
    bitmap: Bitmap?,
    waiting: String,
    drawOnTap: Boolean,
) {
    val size = LocalSize.current
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .clickable(
                actionStartActivity(
                    MainActivity::class.java,
                    actionParametersOf(OpenToday to true, Draw to drawOnTap),
                ),
            )
            .padding(Padding),
        contentAlignment = Alignment.Center,
    ) {
        val label: String
        val line: String?
        val description: String
        val card: ImageProvider
        if (reading != null && bitmap != null) {
            val nameKo = app.catalog.nameKo(reading.cardId)
            val orientation = context.getString(if (reading.reversed) R.string.reversed else R.string.upright)
            description = "$nameKo (${Deck[reading.cardId].name}), $orientation"
            label = "${shortDate(reading.day)}  ·  $nameKo $orientation"
            line = reading.headline ?: reading.keywords.joinToString(" · ").ifEmpty { null }
            card = ImageProvider(bitmap)
        } else {
            // No card for today yet: the face-down card and what is being waited for.
            description = context.getString(R.string.card_back)
            label = today?.let(::shortDate).orEmpty()
            line = waiting
            card = ImageProvider(R.drawable.card_back)
        }
        val plan = planWidget(
            (size.width - Padding * 2).value,
            (size.height - Padding * 2).value,
            line,
            context.resources.configuration.fontScale,
        )
        when (plan) {
            is WidgetPlan.Stacked -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CardImage(card, description, plan.cardHeight.dp)
                Spacer(GlanceModifier.height(Gap))
                Caption(label, line, center = true, style = plan.style, width = plan.captionWidth.dp)
            }
            is WidgetPlan.Side -> Row(verticalAlignment = Alignment.CenterVertically) {
                CardImage(card, description, plan.cardHeight.dp)
                Spacer(GlanceModifier.width(Gap))
                Caption(label, line, center = false, style = plan.style, width = plan.captionWidth.dp)
            }
            is WidgetPlan.CardOnly -> CardImage(card, description, plan.cardHeight.dp)
        }
    }
}

/** Font size and the number of lines the fortune needs at that size. */
internal data class CaptionStyle(val lineSp: Float, val lines: Int) {
    val labelSp: Float get() = maxOf(9f, lineSp - 2f)
}

internal sealed interface WidgetPlan {
    data class Stacked(val cardHeight: Float, val captionWidth: Float, val style: CaptionStyle) : WidgetPlan
    data class Side(val cardHeight: Float, val captionWidth: Float, val style: CaptionStyle) : WidgetPlan
    data class CardOnly(val cardHeight: Float) : WidgetPlan
}

private val CaptionSizes = listOf(13f, 12f, 11f, 10f)
private const val MIN_CARD_HEIGHT = 110f
private const val MIN_SIDE_CAPTION = 110f
private const val MAX_CAPTION_WIDTH = 280f
private const val GAP = 10f

/**
 * Picks the largest caption text that fits whole. The text shrinks before the caption is
 * dropped; only cells too small even at the smallest size show the card alone. Sizes in dp.
 */
internal fun planWidget(width: Float, height: Float, line: String?, fontScale: Float): WidgetPlan {
    for (sp in CaptionSizes) {
        val stackedWidth = minOf(width, MAX_CAPTION_WIDTH)
        val stackedStyle = CaptionStyle(sp, captionLines(line, stackedWidth, sp, fontScale))
        val stackedCard = minOf(height - captionHeight(stackedStyle, fontScale) - GAP, width / CARD_RATIO)
        if (stackedCard >= MIN_CARD_HEIGHT) {
            return WidgetPlan.Stacked(stackedCard, maxOf(stackedWidth, stackedCard * CARD_RATIO), stackedStyle)
        }
        val sideWidth = width - height * CARD_RATIO - GAP
        if (sideWidth >= MIN_SIDE_CAPTION) {
            val sideStyle = CaptionStyle(sp, captionLines(line, sideWidth, sp, fontScale))
            if (captionHeight(sideStyle, fontScale) <= height) return WidgetPlan.Side(height, sideWidth, sideStyle)
        }
    }
    return WidgetPlan.CardOnly(minOf(height, width / CARD_RATIO))
}

private const val LINE_HEIGHT = 1.5f // Serif CJK glyphs need generous leading.
private const val CAPTION_PADDING_H = 24f
private const val CAPTION_PADDING_V = 18f

/** Lines the fortune needs at this caption width, wrapping only at spaces (see [keepWordsTogether]). */
internal fun captionLines(line: String?, captionWidth: Float, sp: Float, fontScale: Float): Int {
    if (line == null) return 0
    val available = (captionWidth - CAPTION_PADDING_H - 4f) / (sp * fontScale)
    if (available <= 0f) return Int.MAX_VALUE / 4
    var lines = 1
    var used = 0f
    for (word in line.split(' ')) {
        val w = word.sumOf { glyphEms(it).toDouble() }.toFloat()
        val needed = if (used == 0f) w else used + SPACE_EMS + w
        if (needed <= available) {
            used = needed
        } else {
            if (used > 0f) lines++
            used = w
            while (used > available) { // A single word wider than the caption.
                lines++
                used -= available
            }
        }
    }
    return lines
}

internal fun captionHeight(style: CaptionStyle, fontScale: Float): Float =
    CAPTION_PADDING_V + style.labelSp * LINE_HEIGHT * fontScale +
        if (style.lines == 0) 0f else 3f + style.lines * style.lineSp * LINE_HEIGHT * fontScale

private const val SPACE_EMS = 0.3f

// Hangul and CJK are full-width; Latin, digits and punctuation are roughly half.
private fun glyphEms(char: Char): Float = when {
    char.code in 0xAC00..0xD7A3 || char.code in 0x3130..0x318F || char.code in 0x4E00..0x9FFF -> 1f
    char == ' ' -> SPACE_EMS
    else -> 0.6f
}

@Composable
private fun CardImage(card: ImageProvider, description: String, height: Dp) {
    Image(
        card, description,
        GlanceModifier.size(height * CARD_RATIO, height).cornerRadius(height / 40),
        contentScale = ContentScale.Fit,
    )
}

/** A small frosted label so the text reads on any wallpaper. */
@Composable
private fun Caption(label: String, line: String?, center: Boolean, style: CaptionStyle, width: Dp? = null) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(
        GlanceModifier
            .then(if (width != null) GlanceModifier.width(width) else GlanceModifier)
            .background(ImageProvider(R.drawable.widget_caption))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            label,
            style = TextStyle(color = accent, fontSize = style.labelSp.sp, fontWeight = FontWeight.Medium, textAlign = align),
            maxLines = 1,
        )
        line?.let {
            Spacer(GlanceModifier.height(3.dp))
            Text(
                keepWordsTogether(it),
                style = TextStyle(color = text, fontSize = style.lineSp.sp, fontFamily = FontFamily.Serif, textAlign = align),
                // One spare line absorbs estimation error instead of cutting the fortune short.
                maxLines = style.lines + 1,
            )
        }
    }
}

private val OpenToday = ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_TODAY)
private val Draw = ActionParameters.Key<Boolean>(MainActivity.EXTRA_DRAW)

private fun shortDate(day: Day) = "${day.month}.${day.dayOfMonth} ${day.weekdayKo()}"

/**
 * RemoteViews cannot opt into phrase-based line breaking, so Korean would wrap mid-word.
 * Invisible word joiners between the letters of each word leave the spaces as the only breaks.
 */
internal fun keepWordsTogether(text: String): String =
    text.split(' ').joinToString(" ") { word -> word.toList().joinToString("\u2060") }

class DailyTarotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyTarotWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        reconcile(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        reconcile(context)
    }

    private fun reconcile(context: Context) {
        val app = context.applicationContext as TarotApplication
        app.widgetChanges.update { it + 1 }
        app.applicationScope.launch { runCatching { RefreshScheduler.reconcile(app) } }
    }
}
