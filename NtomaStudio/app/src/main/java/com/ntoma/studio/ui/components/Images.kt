package com.ntoma.studio.ui.components

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ntoma.studio.R
import java.io.File

/** Loads a local file path or content uri through Coil with a neutral placeholder. */
@Composable
fun LocalImage(
    uri: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val model = remember(uri) {
        if (uri.startsWith("content://") || uri.startsWith("file://")) uri
        else File(uri)
    }
    val placeholder = androidx.compose.ui.graphics.painter.ColorPainter(
        androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
    )
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(model)
            .crossfade(200)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholder,
        error = placeholder,
    )
}

/** Pinch-to-zoom + pan image viewer used on the result screen. */
@Composable
fun ZoomableImage(uri: String, contentDescription: String?, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LocalImage(
        uri = uri,
        contentDescription = contentDescription,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
    )
}

@Composable
fun imagePlaceholderDescription(): String = androidx.compose.ui.res.stringResource(R.string.cd_fabric_image)
