package com.ntoma.studio.ui.screens.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ntoma.studio.R
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.Collection
import com.ntoma.studio.domain.model.CollectionItem
import com.ntoma.studio.domain.model.CollectionItemType
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.GeneratedLook
import com.ntoma.studio.ui.components.GarmentArt
import com.ntoma.studio.ui.components.LocalImage
import com.ntoma.studio.ui.navigation.Routes
import com.ntoma.studio.ui.util.resolveKey
import kotlinx.coroutines.launch

@Composable
fun CollectionDetailScreen(
    nav: NavHostController,
    padding: PaddingValues,
    collectionId: Long,
) {
    val context = LocalContext.current
    val container = AppContainer.get(context)
    val collection by container.collections.observeCollection(collectionId)
        .collectAsState(initial = null as Collection?)
    val fabrics by container.fabricAnalysis.observeAll()
        .collectAsState(initial = emptyList<Fabric>())
    val looks by container.tryOn.observeLooks()
        .collectAsState(initial = emptyList<GeneratedLook>())
    val styles by container.dressStyles.observeAll()
        .collectAsState(initial = emptyList<DressStyle>())
    var tab by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    val tabs = listOf(
        stringResource(R.string.collections_fabrics),
        stringResource(R.string.collections_designs),
        stringResource(R.string.collections_looks),
    )
    val typeForTab = when (tab) {
        0 -> CollectionItemType.FABRIC
        1 -> CollectionItemType.DESIGN
        else -> CollectionItemType.LOOK
    }
    val tabItems = collection?.items.orEmpty().filter { it.type == typeForTab }

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
                text = collection?.name ?: stringResource(R.string.collections_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }

        if (tabItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.collections_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(tabItems, key = { it.itemId }) { item ->
                        CollectionItemCard(
                            item = item,
                            fabrics = fabrics,
                            looks = looks,
                            styles = styles,
                            onRemove = {
                                scope.launch {
                                    container.collections.removeItem(collectionId, item.type, item.itemId)
                                }
                            },
                            onClick = {
                                when (item.type) {
                                    CollectionItemType.FABRIC ->
                                        item.itemId.toLongOrNull()
                                            ?.let { nav.navigate(Routes.fabric(it)) }
                                    CollectionItemType.DESIGN ->
                                        nav.navigate(Routes.design(item.itemId))
                                    CollectionItemType.LOOK ->
                                        item.itemId.toLongOrNull()
                                            ?.let { nav.navigate(Routes.result(it)) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionItemCard(
    item: CollectionItem,
    fabrics: List<Fabric>,
    looks: List<GeneratedLook>,
    styles: List<DressStyle>,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val title: String
    when (item.type) {
        CollectionItemType.FABRIC -> {
            val f = fabrics.firstOrNull { it.id.toString() == item.itemId }
            title = f?.name ?: item.itemId
        }
        CollectionItemType.LOOK -> {
            val l = looks.firstOrNull { it.id.toString() == item.itemId }
            val style = styles.firstOrNull { it.id == l?.dressStyleId }
            title = context.resolveKey(style?.titleKey) ?: stringResource(R.string.result_title)
        }
        CollectionItemType.DESIGN -> {
            val s = styles.firstOrNull { it.id == item.itemId }
            title = context.resolveKey(s?.titleKey) ?: item.itemId
        }
    }
    Card(
        onClick = onClick,
        modifier = Modifier.size(width = 140.dp, height = 210.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column {
            when (item.type) {
                CollectionItemType.FABRIC -> {
                    val f = fabrics.firstOrNull { it.id.toString() == item.itemId }
                    if (f != null) {
                        LocalImage(
                            uri = f.imageUri,
                            contentDescription = title,
                            modifier = Modifier.fillMaxWidth().height(150.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(150.dp))
                    }
                }
                CollectionItemType.LOOK -> {
                    val l = looks.firstOrNull { it.id.toString() == item.itemId }
                    if (l != null) {
                        LocalImage(
                            uri = l.resultImageUri,
                            contentDescription = title,
                            modifier = Modifier.fillMaxWidth().height(150.dp)
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(150.dp))
                    }
                }
                CollectionItemType.DESIGN -> {
                    val s = styles.firstOrNull { it.id == item.itemId }
                    if (s != null) {
                        GarmentArt(
                            style = s,
                            fabric = null,
                            modifier = Modifier.fillMaxWidth().height(150.dp).padding(8.dp),
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(150.dp))
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 2.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
