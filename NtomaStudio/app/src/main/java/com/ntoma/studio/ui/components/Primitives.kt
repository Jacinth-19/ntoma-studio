package com.ntoma.studio.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ntoma.studio.domain.model.AnalyzedColor

@Composable
fun SectionHeader(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    @StringRes actionLabel: Int? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 20.dp)
                    .background(
                        com.ntoma.studio.ui.theme.BrandGold,
                        androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                    ),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        }
        if (actionLabel != null && onAction != null) {
            Text(
                stringResource(actionLabel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp).let { m -> m },
            )
        }
    }
}

@Composable
fun ColorDot(color: AnalyzedColor, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .background(Color(color.argb), CircleShape),
    )
}

@Composable
fun ChipRow(
    chips: List<FilterChipData>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            FilterChip(
                selected = chip.selected,
                onClick = chip.onClick,
                label = { Text(stringResource(chip.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

data class FilterChipData(
    val labelRes: Int,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/** Loading placeholder with a soft moving highlight; mirrors the brand surface tints. */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small,
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .drawWithContent {
                drawContent()
                val w = size.width
                val sweep = w * 0.7f
                val x = -sweep + (w + 2 * sweep) * phase
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                        startX = x - sweep / 2,
                        endX = x + sweep / 2,
                    ),
                )
            },
    )
}
