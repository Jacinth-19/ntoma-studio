package com.ntoma.studio.ui.screens.wardrobe

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.Outfit
import com.ntoma.studio.domain.model.WardrobeCategory
import com.ntoma.studio.domain.model.WardrobeItem
import com.ntoma.studio.ui.components.LocalImage
import kotlinx.coroutines.launch

@Composable
fun WardrobeScreen(nav: NavHostController, padding: PaddingValues) {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val items by container.wardrobe.observeItems()
        .collectAsState(initial = emptyList<WardrobeItem>())
    val outfits by container.wardrobe.observeOutfits()
        .collectAsState(initial = emptyList<Outfit>())
    var tab by remember { mutableStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var showBuilder by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                stringResource(R.string.wardrobe_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.wardrobe_tab_items)) },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.wardrobe_tab_outfits)) },
            )
        }

        if (tab == 0) {
            if (items.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        stringResource(R.string.wardrobe_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.wardrobe_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = { showAdd = true }) {
                        Text(stringResource(R.string.wardrobe_add))
                    }
                }
            } else {
                Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = { showAdd = true },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.wardrobe_add)) },
                        )
                    },
                ) { fabPadding ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(fabPadding),
                    ) {
                        items(items, key = { it.id }) { item ->
                            WardrobeItemCard(
                                item = item,
                                onDelete = { scope.launch { container.wardrobe.deleteItem(item.id) } },
                            )
                        }
                    }
                }
            }
        } else {
            if (outfits.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        stringResource(R.string.outfits_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.outfits_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        onClick = { showBuilder = true },
                        enabled = items.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.outfit_builder_title))
                    }
                }
            } else {
                Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            onClick = { showBuilder = true },
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.outfit_builder_title)) },
                        )
                    },
                ) { fabPadding ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(fabPadding),
                    ) {
                        items(outfits, key = { it.id }) { outfit ->
                            OutfitCard(
                                outfit = outfit,
                                items = items,
                                onDelete = { scope.launch { container.wardrobe.deleteOutfit(outfit.id) } },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddItemDialog(
            onDismiss = { showAdd = false },
            onSave = { name, category, imageUri, notes ->
                showAdd = false
                scope.launch {
                    container.wardrobe.addItem(
                        WardrobeItem(
                            name = name,
                            category = category,
                            imageUri = imageUri,
                            notes = notes,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            },
        )
    }

    if (showBuilder) {
        OutfitBuilderDialog(
            items = items,
            onDismiss = { showBuilder = false },
            onSave = { name, ids ->
                showBuilder = false
                scope.launch { container.wardrobe.saveOutfit(name, ids) }
            },
        )
    }
}

@Composable
private fun WardrobeItemCard(
    item: WardrobeItem,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            if (item.imageUri.isNotBlank()) {
                LocalImage(
                    uri = item.imageUri,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                )
            }
            Row(
                Modifier.padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(item.category.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OutfitCard(
    outfit: Outfit,
    items: List<WardrobeItem>,
    onDelete: () -> Unit,
) {
    val outfitItems = items.filter { it.id in outfit.itemIds }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(140.dp).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                outfitItems.take(3).forEach { item ->
                    LocalImage(
                        uri = item.imageUri,
                        contentDescription = item.name,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
            Row(
                Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    outfit.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, category: WardrobeCategory, imageUri: String, notes: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(WardrobeCategory.TOP) }
    var imageUri by remember { mutableStateOf("") }
    var removeBg by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val dialogContext = LocalContext.current
    val dialogScope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { imageUri = it.toString() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wardrobe_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.wardrobe_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                    OutlinedTextField(
                        value = stringResource(category.labelRes),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        WardrobeCategory.entries.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(stringResource(c.labelRes)) },
                                onClick = { category = c; menuOpen = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text(stringResource(R.string.wardrobe_notes_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (imageUri.isBlank()) {
                            stringResource(R.string.wardrobe_add_photo)
                        } else {
                            stringResource(R.string.wardrobe_change_photo)
                        }
                    )
                }
                if (imageUri.isNotBlank()) {
                    LocalImage(
                        uri = imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = removeBg, onCheckedChange = { removeBg = it })
                        Column {
                            Text(stringResource(R.string.wardrobe_remove_bg), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.wardrobe_remove_bg_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val chosenUri = imageUri
                        val trimName = name.trim()
                        val trimNotes = notes.trim().ifBlank { null }
                        dialogScope.launch {
                            val finalUri = if (removeBg && chosenUri.isNotBlank()) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    cutoutToMedia(dialogContext, chosenUri)
                                } ?: chosenUri
                            } else chosenUri
                            onSave(trimName, category, finalUri, trimNotes)
                        }
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun OutfitBuilderDialog(
    items: List<WardrobeItem>,
    onDismiss: () -> Unit,
    onSave: (name: String, itemIds: List<Long>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.outfit_builder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.outfit_builder_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (items.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val picked = com.ntoma.studio.domain.engine.suggestOutfitIds(
                                items,
                                System.currentTimeMillis(),
                            )
                            if (picked.isNotEmpty()) selected = picked.toSet()
                        },
                    ) { Text(stringResource(R.string.outfit_builder_surprise)) }
                }
                items.forEach { item ->
                    val isSelected = item.id in selected
                    Card(
                        onClick = {
                            selected = if (isSelected) selected - item.id else selected + item.id
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (item.imageUri.isNotBlank()) {
                                LocalImage(
                                    uri = item.imageUri,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                )
                            }
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
                if (selected.isEmpty()) {
                    Text(
                        stringResource(R.string.outfit_builder_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && selected.isNotEmpty()) {
                        onSave(name.trim(), selected.toList())
                    }
                },
            ) { Text(stringResource(R.string.outfit_builder_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Plain-background cutout to a PNG in filesDir/media; null on any failure (caller keeps the
 *  original uri, so a cutout never blocks saving). */
private fun cutoutToMedia(context: android.content.Context, uriString: String): String? = try {
    val src = context.contentResolver.openInputStream(android.net.Uri.parse(uriString))
        ?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
    val scale = (1024f / maxOf(src.width, src.height)).coerceAtMost(1f)
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    val scaled = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
    val px = IntArray(w * h)
    scaled.getPixels(px, 0, w, 0, 0, w, h)
    val cut = com.ntoma.studio.media.BackgroundRemover.remove(px, w, h)
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.setPixels(cut, 0, w, 0, 0, w, h)
    val dir = java.io.File(context.filesDir, "media").apply { mkdirs() }
    val out = java.io.File(dir, "wardrobe_${System.currentTimeMillis()}.png")
    out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    if (!src.isRecycled) src.recycle()
    if (scaled !== src && !scaled.isRecycled) scaled.recycle()
    if (!bmp.isRecycled) bmp.recycle()
    out.absolutePath
} catch (e: Exception) {
    null
}
