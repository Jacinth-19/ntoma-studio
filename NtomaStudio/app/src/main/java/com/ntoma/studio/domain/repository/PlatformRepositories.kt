package com.ntoma.studio.domain.repository

import com.ntoma.studio.domain.model.Collection
import com.ntoma.studio.domain.model.CollectionItemType
import com.ntoma.studio.domain.model.Measurements
import com.ntoma.studio.domain.model.Outfit
import com.ntoma.studio.domain.model.Tailor
import com.ntoma.studio.domain.model.WardrobeItem
import kotlinx.coroutines.flow.Flow

interface CollectionsRepository {
    fun observeCollections(): Flow<List<Collection>>
    fun observeCollection(id: Long): Flow<Collection?>
    suspend fun create(name: String): Long
    suspend fun delete(id: Long)
    suspend fun addItem(collectionId: Long, type: CollectionItemType, itemId: String)
    suspend fun removeItem(collectionId: Long, type: CollectionItemType, itemId: String)
    suspend fun isInAnyCollection(type: CollectionItemType, itemId: String): Boolean
}

interface WardrobeRepository {
    fun observeItems(): Flow<List<WardrobeItem>>
    fun observeOutfits(): Flow<List<Outfit>>
    suspend fun addItem(item: WardrobeItem): Long
    suspend fun deleteItem(id: Long)
    suspend fun getItem(id: Long): WardrobeItem?
    suspend fun saveOutfit(name: String, itemIds: List<Long>): Long
    suspend fun deleteOutfit(id: Long)
}

interface MeasurementsRepository {
    suspend fun get(): Measurements
    suspend fun save(measurements: Measurements)
    suspend fun clear()
}

/** Explicit feedback loop: "not interested" dismissals and resets. */
interface FeedbackRepository {
    fun observeDismissed(): Flow<Set<String>>
    suspend fun dismiss(styleId: String)
    suspend fun undo(styleId: String)
    suspend fun resetAll()
}

/**
 * Tailor/designer directory. The bundled implementation serves clearly-labelled DEMO listings;
 * a production [TailorRepository] will page real, verified businesses from the backend.
 */
interface TailorRepository {
    suspend fun all(): List<Tailor>
    fun observeAll(): Flow<List<Tailor>>
}
