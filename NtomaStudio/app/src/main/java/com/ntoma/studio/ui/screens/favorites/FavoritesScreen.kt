package com.ntoma.studio.ui.screens.favorites

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.data.local.prefs.TooltipStore
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.ui.components.EmptyState
import com.ntoma.studio.ui.components.FabricSwatch
import com.ntoma.studio.ui.components.FirstRunTooltip
import com.ntoma.studio.ui.components.GarmentArt
import com.ntoma.studio.ui.components.LocalImage
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.navigation.appViewModel
import com.ntoma.studio.ui.util.shareImagePath
import com.ntoma.studio.ui.util.shareLook
import com.ntoma.studio.ui.util.shareTextOnly
import com.ntoma.studio.ui.util.timeAgo
import kotlinx.coroutines.launch
import com.ntoma.studio.ui.util.resolveKey

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(nav: NavHostController, padding: PaddingValues) {
    val vm: FavoritesViewModel = appViewModel { c, ctx -> FavoritesViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    val tooltips = AppContainer.get(context).tooltips
    val tooltipSeen by tooltips.seen(TooltipStore.FAVORITES).collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    val collections by AppContainer.get(context).collections.observeCollections()
        .collectAsState(initial = emptyList<com.ntoma.studio.domain.model.Collection>())
    val allTailors by AppContainer.get(context).tailors.observeAll()
        .collectAsState(initial = emptyList<com.ntoma.studio.domain.model.Tailor>())
    val prefs by AppContainer.get(context).settings.preferences
        .collectAsState(initial = com.ntoma.studio.domain.model.UserPreferences())

    Box(Modifier.fillMaxSize().padding(padding)) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                vm.refresh { refreshing = false }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.favorites_title),
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.favorites_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                singleLine = true,
            )
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(
                    R.string.favorites_tab_fabrics,
                    R.string.favorites_tab_designs,
                    R.string.favorites_tab_looks,
                    R.string.favorites_tab_collections,
                    R.string.favorites_tab_tailors,
                ).forEachIndexed { i, res ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) })
                }
            }

            val q = query.trim()
            when (tab) {
                0 -> {
                    val fabrics = state.fabrics.filter {
                        q.isEmpty() ||
                            (it.name ?: context.getString(it.category.labelRes)).contains(q, ignoreCase = true)
                    }
                    if (fabrics.isEmpty()) {
                        EmptyState(
                            R.drawable.illust_empty_collection,
                            R.string.favorites_empty_title,
                            R.string.favorites_empty_body,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp)) {
                            items(fabrics, key = { it.id }) { fabric ->
                                Card(
                                    onClick = { nav.navigate(Routes.fabric(fabric.id)) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        FabricSwatch(fabric, modifier = Modifier.size(64.dp))
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                fabric.name ?: context.getString(fabric.category.labelRes),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                context.timeAgo(fabric.createdAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = {
                                            context.shareImagePath(
                                                fabric.imageUri,
                                                context.getString(
                                                    R.string.share_fabric_text,
                                                    fabric.name ?: context.getString(fabric.category.labelRes),
                                                    context.getString(fabric.pattern.labelRes),
                                                ),
                                            )
                                        }) {
                                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share_item))
                                        }
                                        IconButton(onClick = { vm.unfavoriteFabric(fabric.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    val styles = state.designs.filter {
                        q.isEmpty() || context.resolveKey(it.titleKey).orEmpty().contains(q, ignoreCase = true)
                    }
                    if (styles.isEmpty()) {
                        EmptyState(
                            R.drawable.illust_empty_collection,
                            R.string.favorites_empty_title,
                            R.string.favorites_empty_body,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp)) {
                            items(styles, key = { it.id }) { style ->
                                Card(
                                    onClick = { nav.navigate(Routes.design(style.id)) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        GarmentArt(style, null, modifier = Modifier.width(64.dp).height(80.dp))
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                context.resolveKey(style.titleKey) ?: style.id,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                stringResource(style.gender.labelRes),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = {
                                            context.shareTextOnly(
                                                context.getString(
                                                    R.string.share_design_text,
                                                    context.resolveKey(style.titleKey) ?: style.id,
                                                    context.resolveKey(style.descriptionKey).orEmpty(),
                                                ),
                                            )
                                        }) {
                                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share_item))
                                        }
                                        IconButton(onClick = { vm.unfavoriteDesign(style.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    val looks = state.looks
                    if (looks.isEmpty()) {
                        EmptyState(
                            R.drawable.illust_empty_looks,
                            R.string.empty_looks_title,
                            R.string.empty_looks_body,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp)) {
                            items(looks, key = { it.id }) { look ->
                                Card(
                                    onClick = { nav.navigate(Routes.result(look.id)) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        LocalImage(
                                            look.resultImageUri,
                                            contentDescription = stringResource(R.string.cd_generated_image),
                                            modifier = Modifier.size(72.dp),
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Text(
                                            context.timeAgo(look.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(onClick = {
                                            context.shareLook(look.resultImageUri, "", "")
                                        }) {
                                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.cd_share_item))
                                        }
                                        IconButton(onClick = { vm.unfavoriteLook(look.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    val filtered = collections.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
                    if (filtered.isEmpty()) {
                        EmptyState(
                            R.drawable.illust_empty_collection,
                            R.string.collections_empty_title,
                            R.string.collections_empty_body,
                            modifier = Modifier.weight(1f),
                            actionLabel = R.string.collections_title,
                            onAction = { nav.navigate(Routes.COLLECTIONS) },
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp)) {
                            items(filtered, key = { it.id }) { collection ->
                                Card(
                                    onClick = { nav.navigate(Routes.collection(collection.id)) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                ) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(collection.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                stringResource(R.string.collections_item_count, collection.items.size),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> {
                    val favTailors = allTailors.filter { it.id in prefs.favoriteTailorIds }
                    if (favTailors.isEmpty()) {
                        EmptyState(
                            R.drawable.illust_empty_collection,
                            R.string.favorites_empty_title,
                            R.string.favorites_empty_body,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp)) {
                            items(favTailors, key = { it.id }) { tailor ->
                                Card(
                                    onClick = { nav.navigate(Routes.tailor(tailor.id)) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                ) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(tailor.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${tailor.area}, ${tailor.city} · " +
                                                    stringResource(tailor.priceRange.labelRes),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        if (!tooltipSeen) {
            FirstRunTooltip(
                text = R.string.tooltip_favorites,
                onDismiss = { scope.launch { tooltips.markSeen(TooltipStore.FAVORITES) } },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
