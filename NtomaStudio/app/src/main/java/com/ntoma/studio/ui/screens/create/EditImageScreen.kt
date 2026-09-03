package com.ntoma.studio.ui.screens.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.navigation.appViewModel

@Composable
fun EditImageScreen(nav: NavHostController, padding: PaddingValues, uri: String, target: String) {
    val vm: ImageEditViewModel = appViewModel { c, ctx -> ImageEditViewModel(c, uri, target) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var viewSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    LaunchedEffect(state.resultPath) {
        val path = state.resultPath ?: return@LaunchedEffect
        if (target == "person") {
            nav.navigate(Routes.tryOn(null, null, path)) {
                popUpTo(Routes.CREATE)
            }
        } else {
            nav.navigate(Routes.analysis(path)) {
                popUpTo(Routes.CREATE)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                stringResource(if (target == "person") R.string.edit_title_person else R.string.edit_title_fabric),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .onGloballyPositioned { coords ->
                    viewSize = androidx.compose.ui.geometry.Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                    vm.viewW = coords.size.width
                    vm.viewH = coords.size.height
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> CircularProgressIndicator()
                state.error != null -> Text(
                    context.getString(state.error!!.titleRes),
                    color = MaterialTheme.colorScheme.error,
                )
                state.bitmap != null -> {
                    val bmp = state.bitmap!!
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_fabric_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationZ = state.rotation.toFloat()
                                scaleX = state.scale
                                scaleY = state.scale
                                translationX = state.panX
                                translationY = state.panY
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    vm.onGesture(zoom, pan.x, pan.y)
                                }
                            },
                    )
                    // crop frame
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium),
                    )
                }
            }
        }

        Text(
            stringResource(R.string.edit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CropAspect.values().forEach { aspect ->
                FilterChip(
                    selected = state.aspect == aspect,
                    onClick = { vm.setAspect(aspect) },
                    label = { Text(stringResource(aspect.labelRes)) },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::rotate) {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = stringResource(R.string.edit_rotate))
            }
            OutlinedButton(onClick = vm::reset) { Text(stringResource(R.string.edit_reset)) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.edit_retake)) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = vm::confirm,
                enabled = !state.saving && state.bitmap != null,
            ) {
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.edit_use_photo))
                }
            }
        }
    }
}
