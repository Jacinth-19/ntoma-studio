package com.ntoma.studio.ui.screens.fabric

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.ui.components.ColorDot
import com.ntoma.studio.ui.components.LocalImage
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.util.shareImagePath
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt

@Composable
fun FabricDetailScreen(nav: NavHostController, padding: PaddingValues, fabricId: Long) {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    var fabric by remember { mutableStateOf<Fabric?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCollections by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val collections by AppContainer.get(context).collections.observeCollections()
        .collectAsState(initial = emptyList<com.ntoma.studio.domain.model.Collection>())

    LaunchedEffect(fabricId) {
        container.fabricAnalysis.observeAll().collect { list ->
            fabric = list.firstOrNull { it.id == fabricId }
        }
    }

    val f = fabric
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                f?.name ?: stringResource(R.string.analysis_unnamed),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (f != null) {
                IconButton(onClick = {
                    scope.launch { container.fabricAnalysis.setFavorite(f.id, !f.isFavorite) }
                }) {
                    Icon(
                        if (f.isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(
                            if (f.isFavorite) R.string.cd_favorite_on else R.string.cd_favorite_off,
                        ),
                        tint = if (f.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    context.shareImagePath(
                        f.imageUri,
                        context.getString(
                            R.string.share_fabric_text,
                            f.name ?: context.getString(f.category.labelRes),
                            context.getString(f.pattern.labelRes),
                        ),
                    )
                }) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share_item))
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete_item))
                }
            }
        }

        if (f == null) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(14.dp)) {
                        LocalImage(
                            f.imageUri,
                            contentDescription = stringResource(R.string.cd_fabric_image),
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(context.getString(f.category.labelRes), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.analysis_confidence, (f.confidence * 100).roundToInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            f.colors.forEach { c ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ColorDot(c)
                                    Text(stringResource(c.colorName.labelRes), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${stringResource(f.pattern.labelRes)} · ${stringResource(f.texture.labelRes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { nav.navigate(Routes.recommendations(f.id)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.analysis_action_view_designs))
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { nav.navigate(Routes.tryOn(f.id, null)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.tryon_title))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { nav.navigate(Routes.colorMatch(f.id)) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.tool_color_ideas))
                    }
                    OutlinedButton(
                        onClick = { showCollections = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.collections_add))
                    }
                    OutlinedButton(
                        onClick = { showEdit = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.fabric_edit_title))
                    }
                }
                if (!f.notes.isNullOrBlank() || f.amountCm != null ||
                    !f.intendedWearer.isNullOrBlank() || !f.intendedOccasion.isNullOrBlank()
                ) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        listOfNotNull(
                            f.notes?.takeIf { it.isNotBlank() },
                            f.amountCm?.let { context.getString(R.string.fabric_amount_hint) + ": " + it },
                            f.intendedWearer?.takeIf { it.isNotBlank() },
                            f.intendedOccasion?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete && f != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(context.getString(f.category.labelRes)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.fabricAnalysis.delete(f.id)
                        nav.popBackStack()
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showCollections && f != null) {
        AlertDialog(
            onDismissRequest = { showCollections = false },
            title = { Text(stringResource(R.string.collections_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    com.ntoma.studio.ui.screens.collections.InlineCollectionCreator(
                        onCreated = { newId ->
                            scope.launch {
                                container.collections.addItem(
                                    newId,
                                    com.ntoma.studio.domain.model.CollectionItemType.FABRIC,
                                    f.id.toString(),
                                )
                            }
                            showCollections = false
                        },
                    )
                    if (collections.isEmpty()) {
                        Text(
                            stringResource(R.string.collections_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    collections.forEach { c ->
                        TextButton(onClick = {
                            scope.launch {
                                container.collections.addItem(
                                    c.id,
                                    com.ntoma.studio.domain.model.CollectionItemType.FABRIC,
                                    f.id.toString(),
                                )
                            }
                            showCollections = false
                        }) { Text(c.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCollections = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (showEdit && f != null) {
        var notes by remember(f.id) { mutableStateOf(f.notes ?: "") }
        var amount by remember(f.id) { mutableStateOf(f.amountCm?.toString() ?: "") }
        var wearer by remember(f.id) { mutableStateOf(f.intendedWearer ?: "") }
        var occasion by remember(f.id) { mutableStateOf(f.intendedOccasion ?: "") }
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text(stringResource(R.string.fabric_edit_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text(stringResource(R.string.fabric_notes_hint)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { ch -> ch.isDigit() }.take(4) },
                        placeholder = { Text(stringResource(R.string.fabric_amount_hint)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = wearer,
                        onValueChange = { wearer = it },
                        placeholder = { Text(stringResource(R.string.fabric_wearer_hint)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = occasion,
                        onValueChange = { occasion = it },
                        placeholder = { Text(stringResource(R.string.fabric_occasion_hint)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.fabricAnalysis.updateDetails(
                            id = f.id,
                            notes = notes.trim().ifBlank { null },
                            amountCm = amount.toIntOrNull(),
                            intendedWearer = wearer.trim().ifBlank { null },
                            intendedOccasion = occasion.trim().ifBlank { null },
                        )
                    }
                    showEdit = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEdit = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
