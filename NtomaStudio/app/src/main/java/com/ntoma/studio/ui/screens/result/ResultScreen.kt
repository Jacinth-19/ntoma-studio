package com.ntoma.studio.ui.screens.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.Collection
import com.ntoma.studio.domain.model.CollectionItemType
import com.ntoma.studio.domain.model.GeneratedLook
import com.ntoma.studio.domain.model.TryOnEngine
import com.ntoma.studio.domain.repository.GenderHint
import com.ntoma.studio.domain.repository.TryOnRequest
import com.ntoma.studio.ui.components.BeforeAfterSlider
import com.ntoma.studio.ui.components.LocalImage
import com.ntoma.studio.ui.components.ZoomableImage
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.util.saveToGallery
import com.ntoma.studio.ui.util.shareLook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import com.ntoma.studio.ui.util.resolveKey

@Composable
fun ResultScreen(nav: NavHostController, padding: PaddingValues, lookId: Long) {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    var look by remember { mutableStateOf<GeneratedLook?>(null) }
    var styleTitle by remember { mutableStateOf("") }
    var fabricName by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showCompare by remember { mutableStateOf(false) }
    var showCollections by remember { mutableStateOf(false) }
    var variationsRunning by remember { mutableStateOf(false) }
    var variationsDone by remember { mutableStateOf(0) }
    val allLooks by container.tryOn.observeLooks()
        .collectAsState(initial = emptyList<GeneratedLook>())
    val collections by container.collections.observeCollections()
        .collectAsState(initial = emptyList<Collection>())

    LaunchedEffect(lookId) {
        container.tryOn.observeLooks().collect { looks ->
            look = looks.firstOrNull { it.id == lookId }
            val l = look ?: return@collect
            styleTitle = container.dressStyles.byId(l.dressStyleId)
                ?.let { context.resolveKey(it.titleKey) }.orEmpty()
            fabricName = container.fabricAnalysis.get(l.fabricId)
                ?.let { it.name ?: context.getString(it.category.labelRes) }.orEmpty()
        }
    }

    val l = look
    Box(Modifier.fillMaxSize().padding(padding)) {
        if (l == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.result_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.result_summary, styleTitle, fabricName),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                        )
                        scope.launch { container.tryOn.setFavorite(l.id, !l.isFavorite) }
                    }) {
                        Icon(
                            if (l.isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = stringResource(
                                if (l.isFavorite) R.string.cd_favorite_on else R.string.cd_favorite_off,
                            ),
                            tint = if (l.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (showCompare) {
                        BeforeAfterSlider(
                            beforePath = l.personImageUri,
                            afterPath = l.resultImageUri,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ZoomableImage(
                            l.resultImageUri,
                            contentDescription = stringResource(R.string.cd_generated_image),
                        )
                    }
                    // Disclaimer badge
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                    ) {
                        Text(
                            stringResource(
                                if (l.engine == TryOnEngine.DEMO_COMPOSITE) R.string.result_demo_disclaimer
                                else R.string.result_ai_disclaimer,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                // Variation strip: looks generated from the same base look
                val group = l.variationGroup
                val variations = allLooks.filter {
                    it.id != l.id && group != null && it.variationGroup == group
                }
                if (variations.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(variations, key = { it.id }) { v ->
                            Card(
                                onClick = { nav.navigate(Routes.result(v.id)) },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                LocalImage(
                                    uri = v.resultImageUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(72.dp)
                                        .height(96.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Column(Modifier.padding(16.dp)) {
                    if (variationsRunning) {
                        LinearProgressIndicator(
                            progress = { variationsDone / 3f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.result_variations_progress, variationsDone),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    val remaining = container.entitlements
                                        .remainingTryOnsThisMonth().first()
                                    if (!container.settings.current().premiumEnabled && remaining < 3) {
                                        snackbar.showSnackbar(
                                            context.getString(R.string.result_variations_quota),
                                        )
                                        return@launch
                                    }
                                    variationsRunning = true
                                    val gid = l.variationGroup ?: ("g" + l.id)
                                    for (seed in 1..3) {
                                        container.tryOn.generate(
                                            TryOnRequest(
                                                fabricId = l.fabricId,
                                                dressStyleId = l.dressStyleId,
                                                personImageUri = l.personImageUri,
                                                gender = GenderHint.AUTO,
                                                variationSeed = seed,
                                                groupId = gid,
                                            ),
                                        ).collect { /* progress surfaces via looks flow */ }
                                        variationsDone = seed
                                    }
                                    variationsRunning = false
                                    variationsDone = 0
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) {
                            Text(stringResource(R.string.result_variations))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showCompare = !showCompare },
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Text(stringResource(R.string.result_compare))
                        }
                        OutlinedButton(
                            onClick = { showCollections = true },
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Text(stringResource(R.string.collections_add))
                        }
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.TAILORS) },
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Text(stringResource(R.string.tailor_request_quote))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = {
                            val ok = context.saveToGallery(File(l.resultImageUri))
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (ok) context.getString(R.string.result_saved_to_gallery)
                                    else context.getString(R.string.error_generic_body),
                                )
                            }
                        }) {
                            Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.action_save))
                        }
                        IconButton(onClick = { context.shareLook(l.resultImageUri, styleTitle, fabricName) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share_item))
                        }
                    }
                    Button(
                        onClick = { nav.navigate(Routes.recommendations(l.fabricId)) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text(stringResource(R.string.result_action_another_design))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.tryOn(l.fabricId, l.dressStyleId)) },
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) {
                            Text(stringResource(R.string.result_action_another_person))
                        }
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.HOME) },
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) {
                            Text(stringResource(R.string.result_action_start_over))
                        }
                    }
                }
            }
        }
        if (showCollections && l != null) {
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
                                        CollectionItemType.LOOK,
                                        l.id.toString(),
                                    )
                                    snackbar.showSnackbar(context.getString(R.string.collections_added))
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
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        container.collections.addItem(
                                            c.id,
                                            CollectionItemType.LOOK,
                                            l.id.toString(),
                                        )
                                        snackbar.showSnackbar(
                                            context.getString(R.string.collections_added),
                                        )
                                    }
                                    showCollections = false
                                },
                            ) {
                                Text(c.name)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showCollections = false }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
