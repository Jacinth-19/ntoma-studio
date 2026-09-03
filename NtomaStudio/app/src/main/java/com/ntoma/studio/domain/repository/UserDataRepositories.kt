package com.ntoma.studio.domain.repository

import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.GeneratedLook
import com.ntoma.studio.domain.model.HistoryEvent
import com.ntoma.studio.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val preferences: Flow<UserPreferences>
    suspend fun current(): UserPreferences
    suspend fun update(transform: (UserPreferences) -> UserPreferences)
}

interface FavoritesRepository {
    fun observeFavoriteFabrics(): Flow<List<Fabric>>
    fun observeFavoriteDesigns(): Flow<List<DressStyle>>
    fun observeFavoriteLooks(): Flow<List<GeneratedLook>>
    suspend fun isDesignFavorite(styleId: String): Boolean
    suspend fun setDesignFavorite(styleId: String, favorite: Boolean)
    suspend fun counts(): Triple<Int, Int, Int>
    fun countsFlow(): Flow<Int>
}

interface HistoryRepository {
    fun observeAll(): Flow<List<HistoryEvent>>
    suspend fun record(event: HistoryEvent)
    suspend fun delete(id: Long)
    suspend fun clear()
    suspend fun pruneOlderThan(days: Int)
}

/** Freemium entitlements: daily analyses, monthly try-ons, rewarded bonuses. */
interface EntitlementRepository {
    fun remainingAnalysesToday(): Flow<Int>
    fun remainingTryOnsThisMonth(): Flow<Int>
    suspend fun canAnalyze(): Boolean
    suspend fun canTryOn(): Boolean
    suspend fun consumeAnalysis()
    suspend fun consumeTryOn()
    suspend fun grantBonusTryOn()
}
