package com.lagradost.cloudstream4

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.arrow_back
import com.lagradost.cloudstream4.generated.resources.back
import com.lagradost.cloudstream4.generated.resources.error
import com.lagradost.cloudstream4.generated.resources.error_hint
import com.lagradost.cloudstream4.generated.resources.error_loading
import com.lagradost.cloudstream4.generated.resources.loading
import com.lagradost.cloudstream4.generated.resources.play_arrow
import com.lagradost.cloudstream4.generated.resources.refresh
import com.lagradost.cloudstream4.generated.resources.retry
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * Shared building blocks. Every screen is made from these so that all of them have the same
 * header, spacing, cards, empty states and loading/error states.
 */

val ScreenPadding = 28.dp

/** Width of a poster in the horizontal rows, and the minimum column width of poster grids. */
val PosterWidth = 156.dp

/** Star color of the score badges. */
private val StarColor = Color(0xFFFFC857)

/** Pointing-hand cursor for anything clickable, like every desktop app. */
fun Modifier.handCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/**
 * Keeps text-heavy screens (detail, settings, sources) at a readable width and centered, instead of
 * stretching them across a wide monitor.
 */
@Composable
fun CenteredContent(maxWidth: Dp = 960.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = maxWidth).fillMaxSize(), content = content)
    }
}

/** Same header on every screen: optional back arrow, title (+ subtitle), optional actions on the right. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack != null) 16.dp else ScreenPadding, end = 16.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .handCursor()
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Icon(painterResource(Res.drawable.arrow_back), contentDescription = stringResource(Res.string.back))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** Section title used above rows and groups. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = ScreenPadding, vertical = 6.dp),
    )
}

/** Rounded surface used by every list item, setting group and panel. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), shape),
        content = content,
    )
}

/** Small rounded label (genres, status, quality…). */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

/**
 * Desktop button. [filled] = primary action (accent color), otherwise a soft secondary button.
 * An optional [icon] is drawn before the text.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    icon: DrawableResource? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val container = when {
        filled -> if (hovered) lerp(scheme.primary, Color.White, 0.12f) else scheme.primary
        else -> if (hovered) lerp(scheme.surfaceContainerHigh, scheme.onBackground, 0.10f) else scheme.surfaceContainerHigh
    }
    val content = if (filled) Color.White else scheme.onBackground
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .hoverable(interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .handCursor()
            .padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Text(text, color = content, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun EmptyState(
    icon: DrawableResource,
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}

@Composable
fun CenteredLoading(label: String = stringResource(Res.string.loading)) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun CenteredError(message: String, onRetry: () -> Unit) {
    EmptyState(
        icon = Res.drawable.error,
        message = stringResource(Res.string.error_loading, message),
        hint = stringResource(Res.string.error_hint),
        action = { PillButton(stringResource(Res.string.retry), onRetry, filled = true, icon = Res.drawable.refresh) },
    )
}

/** Animated shimmer fill used by the loading skeletons. */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerProgress",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = lerp(base, MaterialTheme.colorScheme.onBackground, 0.09f)
    return this.drawBehind {
        val w = size.width.coerceAtLeast(1f)
        val start = -w + progress * 3f * w
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(start, 0f),
                end = Offset(start + w, 0f),
            )
        )
    }
}

/** Placeholder poster while a row is loading. */
@Composable
fun PosterSkeleton(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).shimmer())
        Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).shimmer())
    }
}

/** Skeleton of a "title + row of posters" section. */
@Composable
fun PosterRowSkeleton(count: Int = 8) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .padding(horizontal = ScreenPadding, vertical = 6.dp)
                .width(180.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(8.dp))
                .shimmer()
        )
        Row(
            Modifier.padding(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(count) { PosterSkeleton(Modifier.width(PosterWidth)) }
        }
    }
}

/**
 * Horizontal list for desktop: the mouse wheel does not scroll a LazyRow, so it gets arrow buttons
 * on both edges that appear only when there is more to scroll to.
 */
@Composable
fun ScrollableRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = ScreenPadding),
    spacing: Dp = 14.dp,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Box(modifier) {
        LazyRow(
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
        if (state.canScrollBackward) {
            ScrollArrow(Alignment.CenterStart, rotation = 0f) {
                scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - 4).coerceAtLeast(0)) }
            }
        }
        if (state.canScrollForward) {
            ScrollArrow(Alignment.CenterEnd, rotation = 180f) {
                scope.launch { state.animateScrollToItem(state.firstVisibleItemIndex + 4) }
            }
        }
    }
}

@Composable
private fun BoxScope.ScrollArrow(alignment: Alignment, rotation: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .align(alignment)
            .padding(10.dp)
            .size(40.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
            .clickable(onClick = onClick)
            .handCursor(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(Res.drawable.arrow_back),
            contentDescription = null,
            modifier = Modifier.size(20.dp).rotate(rotation),
        )
    }
}

/**
 * Remote image that never leaves an empty hole: while it loads (or if it fails) the first letter of
 * [title] is shown on the surface color underneath, and the image simply covers it when it arrives.
 */
@Composable
fun PosterImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    letterSize: Int = 40,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Text(
            text = title.trim().take(1).uppercase(),
            fontSize = letterSize.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Score badge ("★ 7.8") that sits on top of images. */
@Composable
fun ScoreBadge(score: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.68f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("★", color = StarColor, style = MaterialTheme.typography.labelSmall)
        Text(score, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** Poster with rounded corners, a score badge and the title underneath. Same everywhere. */
@Composable
fun PosterCard(item: SearchResponse, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val score = runCatching { item.score?.toStringNull(0.1, 10) }.getOrNull()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.04f else 1f, tween(160), label = "posterScale")
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .handCursor(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(if (hovered) 14.dp else 0.dp, shape)
                .clip(shape)
                .border(
                    width = 2.dp,
                    color = if (hovered) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = shape,
                ),
        ) {
            PosterImage(item.posterUrl, item.name, Modifier.fillMaxSize())
            if (hovered) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(Res.drawable.play_arrow), null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
            if (score != null) {
                ScoreBadge(score, Modifier.align(Alignment.TopStart).padding(8.dp))
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (hovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
