package com.ntoma.studio.ui.screens.tailors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.Measurements
import com.ntoma.studio.ui.util.resolveKey
import com.ntoma.studio.ui.util.shareTextOnly
import com.ntoma.studio.ui.util.sharePdfFile
import kotlinx.coroutines.launch

/**
 * Demo tailor profile. The "request a quote" flow composes a plain-text design brief the
 * user shares themselves — Ntoma never contacts anyone or processes payments in this build.
 */
@Composable
fun TailorDetailScreen(
    nav: NavHostController,
    padding: PaddingValues,
    tailorId: String,
    preselectedStyleId: String?,
    preselectedFabricId: Long?,
) {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val tailors by container.tailors.observeAll()
        .collectAsState(initial = emptyList<com.ntoma.studio.domain.model.Tailor>())
    val tailor = tailors.firstOrNull { it.id == tailorId }
    val styles by container.dressStyles.observeAll()
        .collectAsState(initial = emptyList<DressStyle>())
    val fabrics by container.fabricAnalysis.observeAll()
        .collectAsState(initial = emptyList<Fabric>())
    val scope = rememberCoroutineScope()

    var showQuote by remember { mutableStateOf(false) }
    var styleId by remember { mutableStateOf(preselectedStyleId) }
    var fabricId by remember { mutableStateOf(preselectedFabricId) }
    var includeMeasurements by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var measurements by remember { mutableStateOf<Measurements?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        measurements = container.measurements.get()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(
                    Icons.Outlined.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                tailor?.name ?: stringResource(R.string.tailors_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (tailor == null) {
            Text(
                stringResource(R.string.tailors_title),
                modifier = Modifier.padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.tailors_demo_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${tailor.area}, ${tailor.city}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.tailor_specialties),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tailor.specialties.forEach { spec ->
                                Text(
                                    stringResource(spec),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            RoundedCornerShape(8.dp),
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                        Text(
                            stringResource(tailor.hoursSummary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(tailor.portfolioNote),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(
                                if (tailor.verified) R.string.tailor_verified else R.string.tailor_not_verified
                            ) + " · " + stringResource(tailor.priceRange.labelRes) +
                                if (tailor.offersDelivery) " · " + stringResource(R.string.tailor_delivery) else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Button(
                    onClick = { showQuote = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tailor_request_quote))
                }
                Text(
                    stringResource(R.string.tailor_quote_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }

    if (showQuote && tailor != null) {
        AlertDialog(
            onDismissRequest = { showQuote = false },
            title = { Text(stringResource(R.string.tailor_quote_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Design picker
                    Text(
                        stringResource(R.string.brief_design, ""),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    styles.take(8).forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = styleId == s.id,
                                onCheckedChange = { styleId = if (it) s.id else null },
                            )
                            Text(
                                context.resolveKey(s.titleKey) ?: s.id,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    // Fabric picker (saved fabrics only)
                    fabrics.take(5).forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = fabricId == f.id,
                                onCheckedChange = { fabricId = if (it) f.id else null },
                            )
                            Text(
                                f.name ?: f.imageUri.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeMeasurements,
                            onCheckedChange = { includeMeasurements = it },
                        )
                        Text(
                            stringResource(R.string.brief_include_measurements),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text(stringResource(R.string.brief_notes_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val style = styles.firstOrNull { it.id == styleId }
                            val fabric = fabrics.firstOrNull { it.id == fabricId }
                            val m = measurements
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val file = com.ntoma.studio.media.BriefPdfWriter.write(
                                    context,
                                    com.ntoma.studio.media.BriefPdfWriter.Brief(
                                        tailorName = tailor.name,
                                        designTitle = style?.let { context.resolveKey(it.titleKey) ?: it.id },
                                        fabricName = fabric?.name,
                                        fabricImageUri = fabric?.imageUri,
                                        customizations = emptyList(),
                                        measurements = if (includeMeasurements && m != null && !m.isEmpty) m else null,
                                        notes = notes.trim().takeIf { it.isNotBlank() },
                                    ),
                                )
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    file?.let {
                                        context.sharePdfFile(it, context.getString(R.string.tailor_quote_title))
                                        com.ntoma.studio.di.AppContainer.get(context).notifier.briefReady(tailor.name)
                                    }
                                    showQuote = false
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.brief_pdf_action)) }
                TextButton(
                    onClick = {
                        val style = styles.firstOrNull { it.id == styleId }
                        val fabric = fabrics.firstOrNull { it.id == fabricId }
                        val lines = buildList {
                            add(context.getString(R.string.app_name) + " — " +
                                context.getString(R.string.tailor_quote_title))
                            add(context.getString(R.string.tailors_title) + ": " + tailor.name)
                            if (style != null) {
                                add(context.getString(
                                    R.string.brief_design,
                                    context.resolveKey(style.titleKey) ?: style.id,
                                ))
                            }
                            if (fabric != null) {
                                add(context.getString(R.string.brief_fabric, fabric.name ?: ""))
                            }
                            val m = measurements
                            if (includeMeasurements && m != null && !m.isEmpty) {
                                add(context.getString(R.string.brief_measurements, m.summary()))
                            }
                            if (notes.isNotBlank()) {
                                add(context.getString(R.string.brief_notes, notes.trim()))
                            }
                        }
                        context.shareTextOnly(lines.joinToString("\n"))
                        showQuote = false
                    },
                ) { Text(stringResource(R.string.tailor_quote_share)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuote = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    // keep scope referenced for future async actions without warnings
    @Suppress("UNUSED_EXPRESSION")
    scope
}

/** Compact, honest summary of the user's own numbers. */
private fun Measurements.summary(): String = buildList {
    heightCm?.let { add("H $it") }
    chestCm?.let { add("C $it") }
    waistCm?.let { add("W $it") }
    hipCm?.let { add("Hip $it") }
    shoulderCm?.let { add("Sh $it") }
    sleeveCm?.let { add("Sl $it") }
    inseamCm?.let { add("In $it") }
    neckCm?.let { add("N $it") }
    notes?.takeIf { it.isNotBlank() }?.let { add(it) }
}.joinToString(", ")
