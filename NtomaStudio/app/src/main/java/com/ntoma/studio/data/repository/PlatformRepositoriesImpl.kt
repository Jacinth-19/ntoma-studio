package com.ntoma.studio.data.repository

import com.ntoma.studio.data.local.db.CollectionDao
import com.ntoma.studio.data.local.db.CollectionEntity
import com.ntoma.studio.data.local.db.CollectionItemEntity
import com.ntoma.studio.data.local.db.MeasurementDao
import com.ntoma.studio.data.local.db.MeasurementEntity
import com.ntoma.studio.data.local.db.OutfitEntity
import com.ntoma.studio.data.local.db.StyleFeedbackDao
import com.ntoma.studio.data.local.db.StyleFeedbackEntity
import com.ntoma.studio.data.local.db.WardrobeDao
import com.ntoma.studio.data.local.db.WardrobeItemEntity
import com.ntoma.studio.domain.model.Collection
import com.ntoma.studio.domain.model.CollectionItem
import com.ntoma.studio.domain.model.CollectionItemType
import com.ntoma.studio.domain.model.Measurements
import com.ntoma.studio.domain.model.Outfit
import com.ntoma.studio.domain.model.WardrobeCategory
import com.ntoma.studio.domain.model.WardrobeItem
import com.ntoma.studio.domain.repository.CollectionsRepository
import com.ntoma.studio.domain.repository.FeedbackRepository
import com.ntoma.studio.domain.repository.MeasurementsRepository
import com.ntoma.studio.domain.repository.WardrobeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class CollectionsRepositoryImpl(
    private val dao: CollectionDao,
) : CollectionsRepository {

    override fun observeCollections(): Flow<List<Collection>> =
        dao.observeAll().map { list ->
            list.map { it.toDomain() }
        }

    override fun observeCollection(id: Long): Flow<Collection?> =
        combine(
            kotlinx.coroutines.flow.flow { emit(dao.byId(id)) },
            dao.observeItems(id),
        ) { entity, items ->
            entity?.toDomain(items.map { it.toDomain() })
        }

    override suspend fun create(name: String): Long =
        dao.insert(CollectionEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    override suspend fun delete(id: Long) {
        dao.deleteItems(id)
        dao.delete(id)
    }

    override suspend fun addItem(collectionId: Long, type: CollectionItemType, itemId: String) {
        dao.addItem(
            CollectionItemEntity(
                collectionId = collectionId,
                itemType = type.name,
                itemId = itemId,
                addedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun removeItem(collectionId: Long, type: CollectionItemType, itemId: String) {
        dao.removeItem(collectionId, type.name, itemId)
    }

    override suspend fun isInAnyCollection(type: CollectionItemType, itemId: String): Boolean =
        dao.itemCount(type.name, itemId) > 0

    private fun CollectionEntity.toDomain(items: List<CollectionItem> = emptyList()) =
        Collection(id = id, name = name, createdAt = createdAt, items = items)

    private fun CollectionItemEntity.toDomain() = CollectionItem(
        type = CollectionItemType.values().firstOrNull { it.name == itemType } ?: CollectionItemType.DESIGN,
        itemId = itemId,
        addedAt = addedAt,
    )
}

class WardrobeRepositoryImpl(
    private val dao: WardrobeDao,
) : WardrobeRepository {

    override fun observeItems(): Flow<List<WardrobeItem>> = dao.observeAll().map { list ->
        list.map {
            WardrobeItem(
                id = it.id,
                name = it.name,
                category = WardrobeCategory.values().firstOrNull { c -> c.name == it.category } ?: WardrobeCategory.TOP,
                imageUri = it.imageUri,
                notes = it.notes,
                createdAt = it.createdAt,
            )
        }
    }

    override fun observeOutfits(): Flow<List<Outfit>> = dao.observeOutfits().map { list ->
        list.map {
            Outfit(
                id = it.id,
                name = it.name,
                itemIds = it.itemIdsCsv.split(',').mapNotNull { s -> s.toLongOrNull() },
                createdAt = it.createdAt,
            )
        }
    }

    override suspend fun addItem(item: WardrobeItem): Long = dao.insert(
        WardrobeItemEntity(
            name = item.name,
            category = item.category.name,
            imageUri = item.imageUri,
            notes = item.notes,
            createdAt = System.currentTimeMillis(),
        ),
    )

    override suspend fun deleteItem(id: Long) = dao.delete(id)

    override suspend fun getItem(id: Long): WardrobeItem? = dao.byId(id)?.let {
        WardrobeItem(
            id = it.id,
            name = it.name,
            category = WardrobeCategory.values().firstOrNull { c -> c.name == it.category } ?: WardrobeCategory.TOP,
            imageUri = it.imageUri,
            notes = it.notes,
            createdAt = it.createdAt,
        )
    }

    override suspend fun saveOutfit(name: String, itemIds: List<Long>): Long = dao.insertOutfit(
        OutfitEntity(
            name = name.trim(),
            itemIdsCsv = itemIds.joinToString(","),
            createdAt = System.currentTimeMillis(),
        ),
    )

    override suspend fun deleteOutfit(id: Long) = dao.deleteOutfit(id)
}

class MeasurementsRepositoryImpl(
    private val dao: MeasurementDao,
) : MeasurementsRepository {

    override suspend fun get(): Measurements = dao.get()?.let {
        Measurements(
            heightCm = it.heightCm, chestCm = it.chestCm, waistCm = it.waistCm, hipCm = it.hipCm,
            shoulderCm = it.shoulderCm, sleeveCm = it.sleeveCm, inseamCm = it.inseamCm,
            neckCm = it.neckCm, notes = it.notes, updatedAt = it.updatedAt,
        )
    } ?: Measurements()

    override suspend fun save(measurements: Measurements) {
        dao.upsert(
            MeasurementEntity(
                heightCm = measurements.heightCm, chestCm = measurements.chestCm,
                waistCm = measurements.waistCm, hipCm = measurements.hipCm,
                shoulderCm = measurements.shoulderCm, sleeveCm = measurements.sleeveCm,
                inseamCm = measurements.inseamCm, neckCm = measurements.neckCm,
                notes = measurements.notes, updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun clear() = dao.clear()
}

class FeedbackRepositoryImpl(
    private val dao: StyleFeedbackDao,
) : FeedbackRepository {

    override fun observeDismissed(): Flow<Set<String>> =
        dao.observeDismissed().map { it.toSet() }

    override suspend fun dismiss(styleId: String) {
        dao.upsert(StyleFeedbackEntity(styleId = styleId, dismissed = 1, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun undo(styleId: String) = dao.delete(styleId)

    override suspend fun resetAll() = dao.clear()
}
