package com.ntoma.studio.ui.screens.design

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
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
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.HistoryEvent
import com.ntoma.studio.ui.components.FabricSwatch
import com.ntoma.studio.ui.components.GarmentArt
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.util.shareTextOnly
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.ntoma.studio.ui.util.resolveKey

@Composable
fun DesignDetailScreen(nav: NavHostController, padding: PaddingValues, styleId: String) {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    var style by remember { mutableStateOf<DressStyle?>(null) }
    var fabrics by remember { mutableStateOf<List<Fabric>>(emptyList()) }
    var selectedFabric by remember { mutableStateOf<Fabric?>(null) }
    var isFavorite by remember { mutableStateOf(false) }
    var showCollections by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val collections by container.collections.observeCollections()
        .collectAsState(initial = emptyList<com.ntoma.studio.domain.model.Collection>())

    LaunchedEffect(styleId) {
        style = container.dressStyles.byId(styleId)
        isFavorite = container.favorites.isDesignFavorite(styleId)
        style?.let { s ->
            container.history.record(
                HistoryEvent(
                    kind = HistoryEvent.Kind.DESIGN_OPEN,
                    labelKey = s.titleKey,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }
    LaunchedEffect(Unit) {
        container.fabricAnalysis.observeAll().collect { list ->
            fabrics = list
            if (selectedFabric == null) selectedFabric = list.firstOrNull()
        }
    }

    val s = style
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                stringResource(R.string.design_detail_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (s != null) {
                IconButton(onClick = {
                    scope.launch {
                        isFavorite = !isFavorite
                        container.favorites.setDesignFavorite(s.id, isFavorite)
                    }
                }) {
                    Icon(
                        if (isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.cd_favorite_on else R.string.cd_favorite_off,
                        ),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    context.shareTextOnly(
                        context.getString(
                            R.string.share_design_text,
                            context.resolveKey(s.titleKey) ?: s.id,
                            context.resolveKey(s.descriptionKey).orEmpty(),
                        ),
                    )
                }) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share_item))
                }
            }
        }

        if (s == null) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            GarmentArt(
                                s,
                                selectedFabric,
                                modifier = Modifier.fillMaxWidth().height(240.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(context.resolveKey(s.titleKey) ?: s.id, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                context.resolveKey(s.descriptionKey).orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    Text(
                        stringResource(R.string.design_detail_occasions),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp),
                    )
                }
                item {
                    Row(
                        Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        s.occasions.forEach { occ ->
                            SuggestionChip(onClick = {}, label = { Text(stringResource(occ.labelRes)) })
                        }
                    }
                }

                item {
                    Text(
                        stringResource(R.string.design_detail_fabrics),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp),
                    )
                }
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        s.affinity.forEach { cat ->
                            Text("• " + stringResource(cat.labelRes), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                if (fabrics.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.design_detail_pick_fabric),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp),
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(fabrics, key = { it.id }) { fabric ->
                                Card(
                                    onClick = { selectedFabric = fabric },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedFabric?.id == fabric.id) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                    ),
                                ) {
                                    FabricSwatch(fabric, modifier = Modifier.width(64.dp).height(64.dp).padding(6.dp))
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }
                item {
                    Button(
                        onClick = { nav.navigate(Routes.tryOn(selectedFabric?.id, s.id)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
                    ) {
                        Text(stringResource(R.string.design_detail_tryon))
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showCollections = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(stringResource(R.string.collections_add))
                        }
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.TAILORS) },
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Text(stringResource(R.string.tailor_request_quote))
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showCollections) {
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
                                    com.ntoma.studio.domain.model.CollectionItemType.DESIGN,
                                    styleId,
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
                                    com.ntoma.studio.domain.model.CollectionItemType.DESIGN,
                                    styleId,
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
}
