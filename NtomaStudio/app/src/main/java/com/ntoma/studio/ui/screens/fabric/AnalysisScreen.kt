package com.ntoma.studio.ui.screens.fabric

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.domain.model.AnalysisEngine
import com.ntoma.studio.ui.components.ColorDot
import com.ntoma.studio.ui.components.ErrorState
import com.ntoma.studio.ui.components.LocalImage
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.navigation.appViewModel
import com.ntoma.studio.ui.util.shareImagePath
import kotlin.math.roundToInt

@Composable
fun AnalysisScreen(nav: NavHostController, padding: PaddingValues, uri: String) {
    val vm: AnalysisViewModel = appViewModel { c, ctx -> AnalysisViewModel(c, uri) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                stringResource(if (state.analyzing) R.string.analysis_title else R.string.analysis_result_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }

        when {
            state.quotaExhausted -> {
                Column(Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.paywall_sheet_title), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.home_quota_exhausted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { nav.navigate(Routes.PREMIUM) }) {
                        Text(stringResource(R.string.profile_row_premium))
                    }
                }
            }
            state.analyzing -> AnalyzingProgress(state.currentStage, state.stages)
            state.error != null -> ErrorState(state.error!!.titleRes, state.error!!.bodyRes, onRetry = vm::retry)
            state.fabric != null -> FabricResult(
                vm = vm,
                nav = nav,
                onShare = { fabric ->
                    context.shareImagePath(
                        fabric.imageUri,
                        context.getString(
                            R.string.share_fabric_text,
                            fabric.name ?: context.getString(fabric.category.labelRes),
                            context.getString(fabric.pattern.labelRes),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun AnalyzingProgress(current: Int, stages: List<Int>) {
    Column(Modifier.padding(28.dp)) {
        LinearProgressIndicator(
            progress = { (current + 1).toFloat() / stages.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        stages.forEachIndexed { index, res ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val done = index < current
                val active = index == current
                val tint by animateColorAsState(
                    when {
                        done -> MaterialTheme.colorScheme.primary
                        active -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                    label = "stage",
                )
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (done) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = tint)
                    } else {
                        Surface(shape = MaterialTheme.shapes.extraSmall, color = tint, modifier = Modifier.size(10.dp)) {}
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(res),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (active || done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        com.ntoma.studio.ui.components.ShimmerBlock(
            Modifier.fillMaxWidth().height(120.dp),
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.height(12.dp))
        com.ntoma.studio.ui.components.ShimmerBlock(Modifier.fillMaxWidth(0.6f).height(22.dp))
        Spacer(Modifier.height(8.dp))
        com.ntoma.studio.ui.components.ShimmerBlock(Modifier.fillMaxWidth(0.8f).height(16.dp))
        Spacer(Modifier.height(8.dp))
        com.ntoma.studio.ui.components.ShimmerBlock(Modifier.fillMaxWidth(0.4f).height(16.dp))
    }
}

@Composable
private fun FabricResult(vm: AnalysisViewModel, nav: NavHostController, onShare: (com.ntoma.studio.domain.model.Fabric) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fabric = state.fabric ?: return
    val context = LocalContext.current

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(14.dp)) {
                    LocalImage(
                        fabric.imageUri,
                        contentDescription = stringResource(R.string.cd_fabric_image),
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                fabric.name ?: context.getString(fabric.category.labelRes),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                stringResource(matchLabelRes(fabric.confidence)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = vm::openRename) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.analysis_rename_title))
                        }
                        IconButton(onClick = vm::toggleFavorite) {
                            Icon(
                                if (fabric.isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = stringResource(
                                    if (fabric.isFavorite) R.string.cd_favorite_on else R.string.cd_favorite_off,
                                ),
                                tint = if (fabric.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onShare(fabric) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share_item))
                        }
                    }
                    Text(
                        stringResource(
                            if (fabric.engine == AnalysisEngine.ON_DEVICE_DEMO) R.string.analysis_engine_on_device_note
                            else R.string.analysis_engine_cloud_note,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { ResultSection(R.string.analysis_section_colors) }
        item {
            Row(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                fabric.colors.forEach { color ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ColorDot(color)
                        Text(
                            stringResource(color.colorName.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        item { ResultSection(R.string.analysis_section_category) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(context.getString(fabric.category.labelRes), style = MaterialTheme.typography.titleMedium)
                Text(
                    context.getString(fabric.category.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.analysis_confidence, (fabric.confidence * 100).roundToInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item { ResultSection(R.string.analysis_section_pattern) }
        item {
            Text(
                stringResource(fabric.pattern.labelRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        item { ResultSection(R.string.analysis_section_texture) }
        item {
            Text(
                stringResource(fabric.texture.labelRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        if (fabric.mlHints.isNotEmpty()) {
            item { ResultSection(R.string.analysis_ml_title) }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    fabric.mlHints.forEach { hint ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                hint.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                stringResource(R.string.analysis_confidence, (hint.score * 100).roundToInt()),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        hint.mapped?.let { m ->
                            Text(
                                stringResource(R.string.analysis_ml_mapped, context.getString(m.labelRes)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        stringResource(R.string.analysis_ml_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (fabric.printCharacteristics.isNotEmpty()) {
            item { ResultSection(R.string.analysis_section_print) }
            item {
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fabric.printCharacteristics.forEach { res ->
                        SuggestionChip(onClick = {}, label = { Text(stringResource(res)) })
                    }
                }
            }
        }

        if (fabric.suggestedUses.isNotEmpty()) {
            item { ResultSection(R.string.analysis_section_uses) }
            item {
                Row(
                    Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fabric.suggestedUses.forEach { occ ->
                        SuggestionChip(onClick = {}, label = { Text(stringResource(occ.labelRes)) })
                    }
                }
            }
        }

        item {
            Text(
                stringResource(R.string.analysis_honesty_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }

        item {
            Button(
                onClick = { nav.navigate(Routes.recommendations(fabric.id)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
            ) {
                Text(stringResource(R.string.analysis_action_view_designs))
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            OutlinedButton(
                onClick = { nav.popBackStack() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
            ) {
                Text(stringResource(R.string.analysis_action_rescan))
            }
        }
    }

    if (state.renameOpen) {
        var text by remember { mutableStateOf(fabric.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = vm::closeRename,
            title = { Text(stringResource(R.string.analysis_rename_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.analysis_rename_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.rename(text) }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = vm::closeRename) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ResultSection(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
    )
}

private fun matchLabelRes(confidence: Float): Int = when {
    confidence >= 0.7f -> R.string.analysis_match_confident
    confidence >= 0.45f -> R.string.analysis_match_possible
    else -> R.string.analysis_match_uncertain
}
