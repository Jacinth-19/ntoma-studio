package com.ntoma.studio.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FabricDao {
    @Insert
    suspend fun insert(fabric: FabricEntity): Long

    @Query("SELECT * FROM fabrics WHERE id = :id")
    suspend fun byId(id: Long): FabricEntity?

    @Query("SELECT * FROM fabrics ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FabricEntity>>

    @Query("SELECT * FROM fabrics WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<FabricEntity>>

    @Query("SELECT COUNT(*) FROM fabrics")
    suspend fun count(): Int

    @Query("UPDATE fabrics SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE fabrics SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Int)

    @Query(
        "UPDATE fabrics SET notes = :notes, amountCm = :amountCm, " +
            "intendedWearer = :intendedWearer, intendedOccasion = :intendedOccasion WHERE id = :id",
    )
    suspend fun updateDetails(id: Long, notes: String?, amountCm: Int?, intendedWearer: String?, intendedOccasion: String?)

    @Query("DELETE FROM fabrics WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM fabrics")
    suspend fun deleteAll()
}

@Dao
interface DressStyleDao {
    @Upsert
    suspend fun upsertAll(styles: List<DressStyleEntity>)

    @Query("SELECT * FROM dress_styles")
    fun observeAll(): Flow<List<DressStyleEntity>>

    @Query("SELECT * FROM dress_styles")
    suspend fun all(): List<DressStyleEntity>

    @Query("SELECT * FROM dress_styles WHERE id = :id")
    suspend fun byId(id: String): DressStyleEntity?

    @Query("SELECT COUNT(*) FROM dress_styles")
    suspend fun count(): Int

    @Insert
    suspend fun addFavorite(favorite: FavoriteStyleEntity)

    @Query("DELETE FROM favorite_styles WHERE styleId = :styleId")
    suspend fun removeFavorite(styleId: String)

    @Query("SELECT COUNT(*) FROM favorite_styles WHERE styleId = :styleId")
    suspend fun isFavorite(styleId: String): Int

    @Query("SELECT s.* FROM dress_styles s INNER JOIN favorite_styles f ON s.id = f.styleId ORDER BY f.addedAt DESC")
    fun observeFavorites(): Flow<List<DressStyleEntity>>

    @Query("SELECT COUNT(*) FROM favorite_styles")
    suspend fun favoriteCount(): Int

    @Query("DELETE FROM favorite_styles")
    suspend fun clearFavorites()
}

@Dao
interface GeneratedLookDao {
    @Insert
    suspend fun insert(look: GeneratedLookEntity): Long

    @Query("SELECT * FROM generated_looks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GeneratedLookEntity>>

    @Query("SELECT * FROM generated_looks WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<GeneratedLookEntity>>

    @Query("SELECT * FROM generated_looks WHERE id = :id")
    suspend fun byId(id: Long): GeneratedLookEntity?

    @Query("UPDATE generated_looks SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Int)

    @Query("DELETE FROM generated_looks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM generated_looks")
    suspend fun count(): Int

    @Query("DELETE FROM generated_looks")
    suspend fun deleteAll()
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(event: HistoryEntity): Long

    @Query("SELECT * FROM history ORDER BY timestamp DESC LIMIT 200")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE timestamp < :cutoff")
    suspend fun prune(cutoff: Long)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage WHERE `key` = :key")
    suspend fun byKey(key: String): UsageEntity?

    @Upsert
    suspend fun upsert(entity: UsageEntity)

    @Query("DELETE FROM usage")
    suspend fun clear()
}
