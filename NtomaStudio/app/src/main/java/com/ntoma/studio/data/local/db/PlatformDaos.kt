package com.ntoma.studio.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Insert
    suspend fun insert(collection: CollectionEntity): Long

    @Query("SELECT * FROM collections ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun byId(id: Long): CollectionEntity?

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM collection_items WHERE collectionId = :id")
    suspend fun deleteItems(id: Long)

    @Insert
    suspend fun addItem(item: CollectionItemEntity): Long

    @Query("SELECT * FROM collection_items WHERE collectionId = :collectionId ORDER BY addedAt DESC")
    fun observeItems(collectionId: Long): Flow<List<CollectionItemEntity>>

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND itemType = :itemType AND itemId = :itemId")
    suspend fun removeItem(collectionId: Long, itemType: String, itemId: String)

    @Query("SELECT COUNT(*) FROM collection_items WHERE itemType = :itemType AND itemId = :itemId")
    suspend fun itemCount(itemType: String, itemId: String): Int
}

@Dao
interface WardrobeDao {
    @Insert
    suspend fun insert(item: WardrobeItemEntity): Long

    @Query("SELECT * FROM wardrobe_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WardrobeItemEntity>>

    @Query("SELECT * FROM wardrobe_items WHERE id = :id")
    suspend fun byId(id: Long): WardrobeItemEntity?

    @Query("DELETE FROM wardrobe_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertOutfit(outfit: OutfitEntity): Long

    @Query("SELECT * FROM outfits ORDER BY createdAt DESC")
    fun observeOutfits(): Flow<List<OutfitEntity>>

    @Query("DELETE FROM outfits WHERE id = :id")
    suspend fun deleteOutfit(id: Long)
}

@Dao
interface MeasurementDao {
    @Insert
    suspend fun upsert(entity: MeasurementEntity)

    @Query("SELECT * FROM measurements WHERE id = 1")
    suspend fun get(): MeasurementEntity?

    @Query("DELETE FROM measurements")
    suspend fun clear()
}

@Dao
interface StyleFeedbackDao {
    @Insert
    suspend fun upsert(entity: StyleFeedbackEntity)

    @Query("SELECT styleId FROM style_feedback WHERE dismissed = 1")
    fun observeDismissed(): Flow<List<String>>

    @Query("SELECT * FROM style_feedback WHERE styleId = :styleId")
    suspend fun byId(styleId: String): StyleFeedbackEntity?

    @Query("DELETE FROM style_feedback WHERE styleId = :styleId")
    suspend fun delete(styleId: String)

    @Query("DELETE FROM style_feedback")
    suspend fun clear()
}
