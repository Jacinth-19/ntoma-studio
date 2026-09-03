package com.ntoma.studio.ui.screens.discover

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.domain.model.FabricCategory
import com.ntoma.studio.ui.components.CardSkeleton
import com.ntoma.studio.ui.components.EmptyState
import com.ntoma.studio.ui.components.FabricSwatch
import com.ntoma.studio.ui.components.GarmentArt
import com.ntoma.studio.ui.components.LocalImage
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.navigation.appViewModel
import com.ntoma.studio.ui.util.resolveKey

@Composable
fun DiscoverScreen(nav: NavHostController, padding: PaddingValues) {
    val vm: DiscoverViewModel = appViewModel { c, ctx -> DiscoverViewModel(c) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Text(
            stringResource(R.string.discover_title),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; vm.setQuery(it) },
            placeholder = { Text(stringResource(R.string.discover_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = ""; vm.setQuery("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear_search))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf(R.string.discover_tab_styles, R.string.discover_tab_fabrics, R.string.discover_tab_looks).forEachIndexed { i, res ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) })
            }
        }

        val q = query.trim()
        when (tab) {
            0 -> {
                if (!state.loaded) {
                    Column(Modifier.padding(20.dp)) { CardSkeleton(); Spacer(Modifier.height(16.dp)); CardSkeleton() }
                } else {
                    val styles = smartFilterStyles(state.styles, q, context)
                    if (styles.isEmpty()) {
                        EmptyState(
                            R.drawable.illust_empty_collection,
                            R.string.discover_empty_search,
                            R.string.discover_empty_search_body,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(styles, key = { it.id }) { style ->
                                Card(
                                    onClick = { nav.navigate(Routes.design(style.id)) },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        GarmentArt(style, null, modifier = Modifier.fillMaxWidth().height(140.dp))
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            context.resolveKey(style.titleKey) ?: style.id,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            stringResource(style.gender.labelRes),
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
            1 -> {
                val cats = FabricCategory.values().filter {
                    q.isEmpty() || context.getString(it.labelRes).contains(q, ignoreCase = true)
                }
                val offline = remember { !context.isOnline() }
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    if (offline) {
                        item {
                            Text(
                                stringResource(R.string.discover_offline_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    items(cats.toList()) { cat ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Row(Modifier.padding(12.dp)) {
                                val catAsset = cat.asset
                                if (catAsset != null) {
                                    com.ntoma.studio.ui.components.AssetImage(
                                        asset = catAsset,
                                        contentDescription = stringResource(cat.labelRes),
                                        modifier = Modifier
                                            .width(64.dp).height(64.dp)
                                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                                    )
                                } else {
                                    FabricSwatch(representativeFabric(cat), modifier = Modifier.width(64.dp).height(64.dp))
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(stringResource(cat.labelRes), style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(cat.descriptionRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                val looks = state.looks.filter { q.isEmpty() }
                if (looks.isEmpty()) {
                    EmptyState(
                        R.drawable.illust_empty_looks,
                        R.string.empty_looks_title,
                        R.string.empty_looks_body,
                        modifier = Modifier.weight(1f),
                        actionLabel = R.string.create_option_person_title,
                        onAction = { nav.navigate(Routes.tryOn(null, null)) },
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(looks, key = { it.id }) { look ->
                            Card(onClick = { nav.navigate(Routes.result(look.id)) }) {
                                LocalImage(
                                    look.resultImageUri,
                                    contentDescription = stringResource(R.string.cd_generated_image),
                                    modifier = Modifier.fillMaxWidth().height(180.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Builds a display-only swatch for the fabric library without pretending to be a scan. */
private fun representativeFabric(cat: FabricCategory): com.ntoma.studio.domain.model.Fabric {
    val palette = when (cat) {
        FabricCategory.KENTE -> listOf(0xFFC8952B, 0xFF8C2F39, 0xFF1F5450, 0xFF14110F)
        FabricCategory.ADINKRA -> listOf(0xFF4A2C17, 0xFF14110F, 0xFFB0803F)
        FabricCategory.WAX, FabricCategory.ANKARA -> listOf(0xFF2F7D4F, 0xFFC8952B, 0xFF8C2F39, 0xFFFDF8F2)
        FabricCategory.BATIK -> listOf(0xFF31556E, 0xFF7FA3BC, 0xFF1D3446)
        FabricCategory.TIEDYE -> listOf(0xFF274B8F, 0xFF8FB0E8, 0xFFFDF8F2)
        FabricCategory.LACE -> listOf(0xFFE8DED2, 0xFFC9B7A4, 0xFFFDF8F2)
        FabricCategory.BROCADE -> listOf(0xFF2E6E6A, 0xFFC8952B)
        FabricCategory.DENIM -> listOf(0xFF2B3A5C, 0xFF3E5379)
        FabricCategory.LINEN -> listOf(0xFFD9CDBB, 0xFFC9BCA7)
        FabricCategory.SILK -> listOf(0xFFB02E43, 0xFFD06A7B)
        FabricCategory.CHIFFON -> listOf(0xFFE7C8D2, 0xFFF6E7EC)
        FabricCategory.VELVET -> listOf(0xFF4A0E1C, 0xFF6E1B2C)
        FabricCategory.COTTON_PLAIN -> listOf(0xFFF3EFE7)
        FabricCategory.FUGU -> listOf(0xFF3A4250, 0xFF14110F, 0xFFF2EFE9)
        FabricCategory.GONJA -> listOf(0xFFB8763A, 0xFFF2EFE9, 0xFF14110F)
        FabricCategory.KENTE_PRINT -> listOf(0xFFC8952B, 0xFF1F5450, 0xFF8C2F39)
        FabricCategory.UNKNOWN -> listOf(0xFF9A8F86)
    }
    return com.ntoma.studio.domain.model.Fabric(
        imageUri = "",
        name = null,
        category = cat,
        colors = palette.map { com.ntoma.studio.domain.model.AnalyzedColor(it.toLong() or 0xFF000000L, com.ntoma.studio.domain.model.ColorName.GOLD, 0.25f) },
        pattern = when (cat) {
            FabricCategory.KENTE, FabricCategory.KENTE_PRINT,
            FabricCategory.FUGU, FabricCategory.GONJA -> com.ntoma.studio.domain.model.PatternType.STRIPED
            FabricCategory.ADINKRA -> com.ntoma.studio.domain.model.PatternType.SYMBOLIC
            FabricCategory.BATIK, FabricCategory.TIEDYE -> com.ntoma.studio.domain.model.PatternType.ORGANIC
            FabricCategory.COTTON_PLAIN, FabricCategory.LINEN, FabricCategory.SILK, FabricCategory.VELVET, FabricCategory.CHIFFON -> com.ntoma.studio.domain.model.PatternType.SOLID
            else -> com.ntoma.studio.domain.model.PatternType.GEOMETRIC
        },
        texture = com.ntoma.studio.domain.model.TextureType.WOVEN,
        confidence = 1f,
        createdAt = 0,
    )
}

/**
 * Natural-language aware filtering: "kente for a wedding" narrows by fabric affinity and
 * occasion instead of demanding an exact title match. Falls back to plain substring search
 * when nothing in the query maps to a known filter.
 */
private fun smartFilterStyles(
    styles: List<com.ntoma.studio.domain.model.DressStyle>,
    q: String,
    context: android.content.Context,
): List<com.ntoma.studio.domain.model.DressStyle> {
    if (q.isEmpty()) return styles
    val intent = com.ntoma.studio.domain.engine.SearchIntentParser.parse(q)
    val hasStructured = intent.occasions.isNotEmpty() || intent.gender != null ||
        intent.category != null || intent.fabricKeywords.isNotEmpty()
    if (!hasStructured) {
        return styles.filter {
            context.resolveKey(it.titleKey).orEmpty().contains(q, ignoreCase = true) ||
                context.resolveKey(it.descriptionKey).orEmpty().contains(q, ignoreCase = true)
        }
    }
    val fabricCats = intent.fabricKeywords.mapNotNull { kw ->
        com.ntoma.studio.domain.model.FabricCategory.values()
            .firstOrNull { it.name.lowercase().startsWith(kw) }
    }.toSet()
    return styles.filter { style ->
        (intent.gender == null || style.gender == intent.gender ||
            style.gender == com.ntoma.studio.domain.model.GenderCategory.UNISEX) &&
            (intent.occasions.isEmpty() || style.occasions.intersect(intent.occasions).isNotEmpty()) &&
            (intent.category == null || style.category == intent.category) &&
            (fabricCats.isEmpty() || style.affinity.intersect(fabricCats).isNotEmpty()) &&
            (intent.remainder.isBlank() ||
                context.resolveKey(style.titleKey).orEmpty().contains(intent.remainder, ignoreCase = true) ||
                context.resolveKey(style.descriptionKey).orEmpty().contains(intent.remainder, ignoreCase = true))
    }
}
