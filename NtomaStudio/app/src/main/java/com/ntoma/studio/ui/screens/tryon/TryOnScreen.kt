package com.ntoma.studio.ui.screens.tryon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.data.local.prefs.TooltipStore
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.AppError
import com.ntoma.studio.domain.model.CustomControl
import com.ntoma.studio.domain.model.CustomizationSupport
import com.ntoma.studio.domain.model.DesignCustomization
import com.ntoma.studio.domain.model.FabricPlacement
import com.ntoma.studio.domain.model.LengthOption
import com.ntoma.studio.domain.model.NecklineOption
import com.ntoma.studio.domain.model.Outcome
import com.ntoma.studio.domain.model.SleeveOption
import com.ntoma.studio.domain.repository.GenderHint
import com.ntoma.studio.domain.repository.TryOnState
import com.ntoma.studio.permissions.rememberCameraPermission
import com.ntoma.studio.ui.components.ErrorState
import com.ntoma.studio.ui.components.FabricSwatch
import com.ntoma.studio.ui.components.FirstRunTooltip
import com.ntoma.studio.ui.components.GarmentArt
import com.ntoma.studio.ui.components.LocalImage
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.navigation.appViewModel
import com.ntoma.studio.ui.util.rememberPhotoPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ntoma.studio.ui.util.resolveKey

@Composable
fun TryOnScreen(
    nav: NavHostController,
    padding: PaddingValues,
    fabricId: Long?,
    styleId: String?,
    personPath: String?,
) {
    val vm: TryOnViewModel = appViewModel { c, ctx -> TryOnViewModel(c, fabricId, styleId, personPath) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tooltips = AppContainer.get(context).tooltips
    val tooltipSeen by tooltips.seen(TooltipStore.TRYON).collectAsState(initial = true)

    val requestCamera = rememberCameraPermission { nav.navigate(Routes.CAMERA_PERSON) }
    val picker = rememberPhotoPicker { uri ->
        when (val v = AppContainer.get(context).imageProcessor.validate(uri)) {
            is Outcome.Success -> nav.navigate(Routes.edit(uri.toString(), "person"))
            is Outcome.Failure -> Unit
        }
    }

    LaunchedEffect(state.tryOn) {
        val st = state.tryOn
        if (st is TryOnState.Success) {
            nav.navigate(Routes.result(st.look.id)) {
                popUpTo(Routes.CREATE)
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                    Text(stringResource(R.string.tryon_title), style = MaterialTheme.typography.titleLarge)
                }
            }

            // Step 1: person photo
            item { StepLabel(R.string.tryon_step_photo) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        if (state.personPath != null) {
                            LocalImage(
                                state.personPath!!,
                                contentDescription = stringResource(R.string.cd_person_image),
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { picker.launch(pickReq()) }) {
                                Text(stringResource(R.string.tryon_photo_replace))
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(stringResource(R.string.tryon_photo_guidance_title), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        stringResource(R.string.tryon_photo_guidance),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = requestCamera) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.tryon_photo_camera))
                                }
                                OutlinedButton(onClick = { picker.launch(pickReq()) }) {
                                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.tryon_photo_gallery))
                                }
                            }
                        }
                    }
                }
            }

            // Step 2: who wears it
            item { StepLabel(R.string.tryon_step_person) }
            item {
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GenderHint.values().forEach { g ->
                        FilterChip(
                            selected = state.gender == g,
                            onClick = { vm.setGender(g) },
                            label = {
                                Text(
                                    stringResource(
                                        when (g) {
                                            GenderHint.WOMAN -> R.string.tryon_gender_woman
                                            GenderHint.MAN -> R.string.tryon_gender_man
                                            GenderHint.AUTO -> R.string.tryon_gender_auto
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            // Step 3: fabric
            item { StepLabel(R.string.tryon_fabric_current) }
            item {
                if (state.fabrics.isEmpty()) {
                    Text(
                        stringResource(R.string.empty_fabrics_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.fabrics, key = { it.id }) { fabric ->
                            Card(
                                onClick = { vm.setFabric(fabric.id) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (state.fabricId == fabric.id) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                ),
                            ) {
                                Column(Modifier.padding(6.dp)) {
                                    FabricSwatch(fabric, modifier = Modifier.size(72.dp))
                                    Text(
                                        fabric.name ?: context.getString(fabric.category.labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Step 4: design
            item { StepLabel(R.string.tryon_step_design) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.styles, key = { it.id }) { style ->
                        Card(
                            onClick = { vm.setStyle(style.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (state.styleId == style.id) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                            ),
                        ) {
                            Column(Modifier.padding(6.dp).width(96.dp)) {
                                GarmentArt(style, null, modifier = Modifier.fillMaxWidth().height(96.dp))
                                Text(
                                    context.resolveKey(style.titleKey) ?: style.id,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }

            // Step 5: customization — only controls the selected design supports
            item {
                val style = state.styles.firstOrNull { it.id == state.styleId }
                if (style != null) {
                    val support = CustomizationSupport.forSilhouette(style.silhouette)
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        StepLabel(R.string.customizer_title)
                        if (CustomControl.SLEEVE in support) {
                            CustomChipRow(stringResource(R.string.customizer_sleeve)) {
                                SleeveOption.entries.forEach { opt ->
                                    FilterChip(
                                        selected = state.customization.sleeve == opt,
                                        onClick = { vm.setCustomization(state.customization.copy(sleeve = opt)) },
                                        label = { Text(stringResource(opt.labelRes)) },
                                    )
                                }
                            }
                        }
                        if (CustomControl.NECKLINE in support) {
                            CustomChipRow(stringResource(R.string.customizer_neckline)) {
                                NecklineOption.entries.forEach { opt ->
                                    FilterChip(
                                        selected = state.customization.neckline == opt,
                                        onClick = { vm.setCustomization(state.customization.copy(neckline = opt)) },
                                        label = { Text(stringResource(opt.labelRes)) },
                                    )
                                }
                            }
                        }
                        if (CustomControl.LENGTH in support) {
                            CustomChipRow(stringResource(R.string.customizer_length)) {
                                LengthOption.entries.forEach { opt ->
                                    FilterChip(
                                        selected = state.customization.length == opt,
                                        onClick = { vm.setCustomization(state.customization.copy(length = opt)) },
                                        label = { Text(stringResource(opt.labelRes)) },
                                    )
                                }
                            }
                        }
                        if (CustomControl.PLACEMENT in support) {
                            CustomChipRow(stringResource(R.string.customizer_placement)) {
                                FabricPlacement.entries.forEach { opt ->
                                    FilterChip(
                                        selected = state.customization.placement == opt,
                                        onClick = { vm.setCustomization(state.customization.copy(placement = opt)) },
                                        label = { Text(stringResource(opt.labelRes)) },
                                    )
                                }
                            }
                            Text(
                                stringResource(state.customization.placement.descriptionRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (CustomControl.ACCENT in support) {
                            CustomChipRow(stringResource(R.string.customizer_accent)) {
                                ACCENT_SWATCHES.forEach { argb ->
                                    val selected = state.customization.accentArgb == argb
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(argb))
                                            .then(
                                                if (selected) {
                                                    Modifier.background(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clickable {
                                                vm.setCustomization(
                                                    state.customization.copy(
                                                        accentArgb = if (selected) null else argb,
                                                    ),
                                                )
                                            },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.customizer_demo_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Quota + start
            item {
                Column(Modifier.padding(20.dp)) {
                    if (!state.premium) {
                        Text(
                            stringResource(R.string.tryon_quota_note, state.quotaLeft, 2),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = vm::start,
                        enabled = !state.running && state.personPath != null && state.fabricId != null && state.styleId != null,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        Text(stringResource(R.string.tryon_start))
                    }
                }
            }

            // Processing / result states
            item {
                when (val st = state.tryOn) {
                    is TryOnState.Uploading, is TryOnState.Processing, is TryOnState.Generating, is TryOnState.Finishing -> {
                        ProcessingPanel(st)
                    }
                    is TryOnState.Error -> ErrorState(st.error.titleRes, st.error.bodyRes, onRetry = vm::start)
                    else -> Unit
                }
            }
        }

        if (!tooltipSeen) {
            FirstRunTooltip(
                text = R.string.tooltip_tryon,
                onDismiss = { scope.launch { tooltips.markSeen(TooltipStore.TRYON) } },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        if (state.paywallOpen) {
            AlertDialog(
                onDismissRequest = vm::dismissPaywall,
                title = { Text(stringResource(R.string.paywall_sheet_title)) },
                text = { Text(stringResource(R.string.paywall_sheet_body_tryons, 2)) },
                confirmButton = {
                    TextButton(onClick = { vm.dismissPaywall(); nav.navigate(Routes.PREMIUM) }) {
                        Text(stringResource(R.string.profile_row_premium))
                    }
                },
                dismissButton = {
                    TextButton(onClick = vm::openRewarded) {
                        Text(stringResource(R.string.paywall_sheet_alternative))
                    }
                },
            )
        }

        if (state.rewardedOpen) {
            RewardedAdDialog(onFinished = vm::rewardedFinished, onDismiss = vm::dismissRewarded)
        }
    }
}

@Composable
private fun StepLabel(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ProcessingPanel(st: TryOnState) {
    val res = when (st) {
        is TryOnState.Uploading -> R.string.tryon_state_uploading
        is TryOnState.Processing -> R.string.tryon_state_processing
        is TryOnState.Generating -> R.string.tryon_state_generating
        is TryOnState.Finishing -> R.string.tryon_state_finishing
        else -> R.string.tryon_state_idle
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(20.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Text(stringResource(res), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.tryon_demo_notice_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RewardedAdDialog(onFinished: () -> Unit, onDismiss: () -> Unit) {
    var secondsLeft by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
        onFinished()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ads_rewarded_label)) },
        text = {
            Column {
                Text(stringResource(R.string.ads_rewarded_playing, secondsLeft))
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { (5 - secondsLeft) / 5f }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ads_rewarded_skip)) }
        },
    )
}

/** A curated set of contrast colours that pair well with Ghanaian prints. */
private val ACCENT_SWATCHES = listOf(
    0xFF1D1A17, // near-black
    0xFFFFF8F0, // cream
    0xFF8C2F39, // terracotta
    0xFF1F5450, // deep teal
    0xFFC8952B, // gold
    0xFF3B4CCA, // royal blue
)

@Composable
private fun CustomChipRow(label: String, content: @Composable () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

private fun pickReq() = androidx.activity.result.PickVisualMediaRequest(
    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
)
