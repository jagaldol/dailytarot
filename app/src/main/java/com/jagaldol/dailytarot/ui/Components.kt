package com.jagaldol.dailytarot.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jagaldol.dailytarot.R
import com.jagaldol.dailytarot.data.CardImages
import com.jagaldol.dailytarot.data.FortuneEntry
import com.jagaldol.dailytarot.model.ContentStatus
import com.jagaldol.dailytarot.model.DailyReading
import com.jagaldol.dailytarot.model.Deck
import com.jagaldol.dailytarot.model.ReadingSource
import com.jagaldol.dailytarot.ui.theme.gold

private val CardShape = RoundedCornerShape(12.dp)

/** Card art; [full] decodes the large asset for the hero card, otherwise a thumbnail. */
@Composable
fun CardFace(
    cardId: Int,
    reversed: Boolean,
    modifier: Modifier = Modifier,
    full: Boolean = false,
    elevation: Dp = 0.dp,
    corner: Dp = 12.dp,
) {
    val card = Deck[cardId]
    val resources = LocalResources.current
    val res = if (full) card.imageRes else card.thumbnailRes
    val initial = if (full) CardImages.cachedFull(res) else CardImages.cachedThumbnail(res)
    val bitmap by produceState(initial, res) {
        value = if (full) CardImages.full(resources, res) else CardImages.thumbnail(resources, res)
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .aspectRatio(3f / 5f)
            .shadow(elevation, shape, ambientColor = Color(0x55000000), spotColor = Color(0x66000000))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = if (reversed) 180f else 0f },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun CardBack(modifier: Modifier = Modifier, elevation: Dp = 0.dp) {
    Image(
        painter = painterResource(R.drawable.card_back),
        contentDescription = null,
        modifier = modifier
            .aspectRatio(3f / 5f)
            .shadow(elevation, CardShape, ambientColor = Color(0x55000000), spotColor = Color(0x66000000))
            .clip(CardShape),
        contentScale = ContentScale.FillBounds,
    )
}

/**
 * Face-down until revealed, then a slow 3D turn. Already revealed cards appear without motion.
 * [onClick] is null when tapping must do nothing (for example while waiting for Lifebase).
 */
@Composable
fun FlipCard(
    cardId: Int?,
    reversed: Boolean,
    revealed: Boolean,
    onClick: (() -> Unit)?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    // The angle is only read inside graphicsLayer, so animation frames skip recomposition and
    // layout; the content recomposes once, when the card turns edge-on and the face swaps in.
    val rotation = animateFloatAsState(
        targetValue = if (revealed && cardId != null) 180f else 0f,
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label = "card flip",
    )
    val showFace by remember { derivedStateOf { rotation.value > 90f } }
    // Decode the large face while the back is still showing, so it is ready at the halfway point.
    val resources = LocalResources.current
    LaunchedEffect(cardId) {
        if (cardId != null) CardImages.full(resources, Deck[cardId].imageRes)
    }
    val density = LocalDensity.current.density
    Box(
        modifier
            .aspectRatio(3f / 5f)
            .graphicsLayer {
                rotationY = rotation.value
                cameraDistance = 16f * density
            }
            .clickable(
                enabled = onClick != null,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = { onClick?.invoke() },
            )
            .semantics { this.contentDescription = contentDescription },
    ) {
        if (!showFace || cardId == null) {
            CardBack(Modifier.fillMaxSize(), elevation = 18.dp)
        } else {
            CardFace(
                cardId, reversed,
                Modifier.fillMaxSize().graphicsLayer { rotationY = 180f },
                full = true, elevation = 18.dp,
            )
        }
    }
}

/** A soft candle glow behind the hero card. */
@Composable
fun Glow(modifier: Modifier = Modifier) {
    val gold = MaterialTheme.colorScheme.gold
    // The gradient is built once per size, not on every draw.
    Spacer(
        modifier.drawWithCache {
            val brush = Brush.radialGradient(
                listOf(gold.copy(alpha = 0.22f), gold.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(size.width / 2, size.height / 2),
                radius = size.minDimension / 2,
            )
            onDrawBehind { drawCircle(brush) }
        },
    )
}

/** ─── ✦ ─── */
@Composable
fun Ornament(modifier: Modifier = Modifier) {
    val gold = MaterialTheme.colorScheme.gold
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(36.dp).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, gold))))
        Icon(
            painterResource(R.drawable.ic_nav_today),
            contentDescription = null,
            tint = gold,
            modifier = Modifier.padding(horizontal = 10.dp).size(12.dp),
        )
        Box(Modifier.width(36.dp).height(1.dp).background(Brush.horizontalGradient(listOf(gold, Color.Transparent))))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordChips(keywords: List<String>, modifier: Modifier = Modifier) {
    if (keywords.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keywords.forEach { keyword ->
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    keyword,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
fun orientationLabel(reversed: Boolean): String =
    stringResource(if (reversed) R.string.reversed else R.string.upright)

@Composable
fun sourceLabel(source: ReadingSource): String = stringResource(
    when (source) {
        ReadingSource.DEFAULT -> R.string.source_default
        ReadingSource.MANUAL -> R.string.source_manual
        ReadingSource.LIFEBASE -> R.string.source_lifebase
        ReadingSource.LEGACY -> R.string.source_legacy
    },
)

@Composable
fun SourceLine(source: ReadingSource, waiting: Boolean, modifier: Modifier = Modifier) {
    val dot = if (source == ReadingSource.LIFEBASE) MaterialTheme.colorScheme.gold else MaterialTheme.colorScheme.outline
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        val text = sourceLabel(source) + if (waiting) " · " + stringResource(R.string.source_waiting) else ""
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Name, fortune, keywords and body of one reading, centered under its card. */
@Composable
fun ReadingText(
    reading: DailyReading,
    name: String,
    defaultFortune: FortuneEntry?,
    waiting: Boolean,
    modifier: Modifier = Modifier,
) {
    val card = Deck[reading.cardId]
    Column(modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            // The English name repeats under a translated one; an English title needs only the orientation.
            if (name == card.name) orientationLabel(reading.reversed).uppercase()
            else "${card.name.uppercase()}  ·  ${orientationLabel(reading.reversed)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        Ornament()
        reading.headline?.let {
            Spacer(Modifier.height(24.dp))
            Text(
                it,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        KeywordChips(reading.keywords)
        reading.body?.let {
            Spacer(Modifier.height(28.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (reading.source == ReadingSource.LIFEBASE &&
            reading.contentStatus == ContentStatus.KEYWORDS_ONLY && defaultFortune != null
        ) {
            Spacer(Modifier.height(28.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.no_interpretation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.default_interpretation),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.gold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(defaultFortune.fortuneText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
        SourceLine(reading.source, waiting)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.gold,
        modifier = modifier,
    )
}

@Composable
fun BackBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack, role = Role.Button),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_back),
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}
