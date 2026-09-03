package com.ntoma.studio.domain.repository

import com.ntoma.studio.domain.model.AppError
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.Outcome
import kotlinx.coroutines.flow.Flow

/** Progress + terminal events emitted while an analysis runs. */
sealed interface AnalysisEvent {
    data class Stage(val stageRes: Int) : AnalysisEvent
    data class Completed(val fabric: Fabric) : AnalysisEvent
    data class Failed(val error: AppError) : AnalysisEvent
}

/**
 * Fabric identification. The default implementation is the on-device demo engine; a cloud CV
 * backend can implement the same interface without any UI change.
 */
interface FabricAnalysisRepository {
    fun analyze(imageUri: String): Flow<AnalysisEvent>
    suspend fun save(fabric: Fabric): Long
    suspend fun get(id: Long): Fabric?
    fun observeAll(): Flow<List<Fabric>>
    fun observeFavorites(): Flow<List<Fabric>>
    suspend fun rename(id: Long, name: String)
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun updateDetails(id: Long, notes: String?, amountCm: Int?, intendedWearer: String?, intendedOccasion: String?)
    suspend fun delete(id: Long)
}

/**
 * The garment catalogue. Backed by Room, seeded from bundled JSON assets today and refreshable
 * from a backend tomorrow.
 */
interface DressStyleRepository {
    fun observeAll(): Flow<List<DressStyle>>
    suspend fun all(): List<DressStyle>
    suspend fun byId(id: String): DressStyle?
    suspend fun refresh(): Outcome<Int>
}
