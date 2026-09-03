package com.ntoma.studio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ntoma.studio.R
import java.io.File

/**
 * Drag-to-reveal comparison between the original photo and the try-on result.
 * Both sides are the real files on disk — nothing is simulated.
 */
@Composable
fun BeforeAfterSlider(
    beforePath: String,
    afterPath: String,
    modifier: Modifier = Modifier
) {
    var fraction by remember { mutableStateOf(0.5f) }
    val leftLabel = stringResource(R.string.result_compare_original)
    val rightLabel = stringResource(R.string.result_compare_result)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    fraction = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                }
            }
    ) {
        // After (result) underneath, full size.
        AsyncImage(
            model = File(afterPath),
            contentDescription = rightLabel,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Before (original) clipped to the left of the handle.
        val clipShape = remember(fraction) {
            object : Shape {
                override fun createOutline(
                    size: androidx.compose.ui.geometry.Size,
                    layoutDirection: LayoutDirection,
                    density: Density
                ): Outline = Outline.Rectangle(Rect(0f, 0f, size.width * fraction, size.height))
            }
        }
        AsyncImage(
            model = File(beforePath),
            contentDescription = leftLabel,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.clip = true
                    this.shape = clipShape
                }
        )
        // Handle knob follows the clip edge
        Box(
            Modifier.fillMaxWidth(fraction),
            contentAlignment = Alignment.CenterEnd
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.offset(x = 14.dp)
            ) {
                Text(
                    text = "◀ ▶",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(text = leftLabel, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Text(text = rightLabel, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}
