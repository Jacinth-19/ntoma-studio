package com.ntoma.studio.ui.screens.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.data.local.prefs.TooltipStore
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.ui.components.FabricSwatch
import com.ntoma.studio.ui.components.FirstRunTooltip
import com.ntoma.studio.ui.components.SectionHeader
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.navigation.appViewModel
import com.ntoma.studio.ui.util.openFabricFlow
import com.ntoma.studio.ui.util.rememberPhotoPicker
import com.ntoma.studio.permissions.rememberCameraPermission
import kotlinx.coroutines.launch

@Composable
fun CreateScreen(nav: NavHostController, padding: PaddingValues) {
    val vm: CreateViewModel = appViewModel { c, ctx -> CreateViewModel(c) }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showSamples by remember { mutableStateOf(false) }
    val tooltips = AppContainer.get(context).tooltips
    val tooltipSeen by tooltips.seen(TooltipStore.CREATE).collectAsState(initial = true)

    val requestCamera = rememberCameraPermission { nav.navigate(Routes.CAMERA_FABRIC) }
    val picker = rememberPhotoPicker { uri ->
        context.openFabricFlow(
            uri,
            navigate = { nav.navigate(it) },
            onError = { e -> scope.launch { snackbar.showSnackbar(context.getString(e.titleRes)) } },
        )
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 28.dp)) {
                    Text(stringResource(R.string.create_title), style = MaterialTheme.typography.displayMedium)
                    Text(
                        stringResource(R.string.create_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                OptionCard(
                    icon = Icons.Filled.CameraAlt,
                    title = R.string.create_option_camera_title,
                    body = R.string.create_option_camera_body,
                    onClick = requestCamera,
                )
            }
            item {
                OptionCard(
                    icon = Icons.Filled.PhotoLibrary,
                    title = R.string.create_option_gallery_title,
                    body = R.string.create_option_gallery_body,
                    onClick = { picker.launch(pickRequest()) },
                )
            }
            item {
                OptionCard(
                    icon = Icons.Filled.Person,
                    title = R.string.create_option_person_title,
                    body = R.string.create_option_person_body,
                    onClick = { nav.navigate(Routes.tryOn(null, null)) },
                )
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                    Text(
                        stringResource(R.string.create_guidance_header),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.tryon_photo_guidance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.recent.isNotEmpty()) {
                item { SectionHeader(R.string.create_recent_title) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.recent, key = { it.id }) { fabric ->
                            Card(
                                onClick = { nav.navigate(Routes.fabric(fabric.id)) },
                                modifier = Modifier.width(140.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    FabricSwatch(fabric, modifier = Modifier.fillMaxWidth().height(90.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        fabric.name ?: context.getString(fabric.category.labelRes),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.create_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                item {
                    androidx.compose.material3.TextButton(
                        onClick = { showSamples = true },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) { Text(stringResource(R.string.sample_cta)) }
                }
            }
        }

        if (!tooltipSeen) {
            FirstRunTooltip(
                text = R.string.tooltip_create,
                onDismiss = { scope.launch { tooltips.markSeen(TooltipStore.CREATE) } },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))

        if (showSamples) {
            com.ntoma.studio.ui.components.SamplePickerDialog(
                onDismiss = { showSamples = false },
                onPicked = { uri ->
                    showSamples = false
                    nav.navigate(Routes.analysis(uri))
                },
            )
        }
    }
}

@Composable
private fun OptionCard(icon: ImageVector, title: Int, body: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun pickRequest() = androidx.activity.result.PickVisualMediaRequest(
    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
)
