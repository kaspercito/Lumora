package com.lagradost.cloudstream4

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.arrow_back
import com.lagradost.cloudstream4.generated.resources.back
import com.lagradost.cloudstream4.generated.resources.error_loading
import com.lagradost.cloudstream4.generated.resources.loading
import com.lagradost.cloudstream4.generated.resources.retry
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/*
 * Shared building blocks. Every screen is made from these so that all of them have the same
 * header, spacing, cards, empty states and loading/error states.
 */

val ScreenPadding = 16.dp

/** Same header on every screen: optional back arrow, title, optional actions on the right. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(painterResource(Res.drawable.arrow_back), contentDescription = stringResource(Res.string.back))
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        actions()
    }
}

/** Section title used above rows and groups. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = ScreenPadding, vertical = 6.dp),
    )
}

@Composable
fun EmptyState(icon: DrawableResource, message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(48.dp).alpha(0.5f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun CenteredLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(
                stringResource(Res.string.loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(Res.string.error_loading, message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            WhiteButton(stringResource(Res.string.retry), onClick = onRetry)
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
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Box(modifier) {
        LazyRow(
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        if (state.canScrollBackward) {
            ScrollArrow(Alignment.CenterStart, rotation = 0f) {
                scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - 3).coerceAtLeast(0)) }
            }
        }
        if (state.canScrollForward) {
            ScrollArrow(Alignment.CenterEnd, rotation = 180f) {
                scope.launch { state.animateScrollToItem(state.firstVisibleItemIndex + 3) }
            }
        }
    }
}

@Composable
private fun BoxScope.ScrollArrow(alignment: Alignment, rotation: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .align(alignment)
            .padding(6.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(Res.drawable.arrow_back),
            contentDescription = null,
            modifier = Modifier.size(20.dp).rotate(rotation),
        )
    }
}

/** Poster with rounded corners, a score badge and the title underneath. Same everywhere. */
@Composable
fun PosterCard(item: SearchResponse, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val score = runCatching { item.score?.toStringNull(0.1, 10) }.getOrNull()
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (score != null) {
                Text(
                    text = "★ $score",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
