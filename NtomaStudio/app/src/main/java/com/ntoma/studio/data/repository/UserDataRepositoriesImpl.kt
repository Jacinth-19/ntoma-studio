package com.ntoma.studio.data.repository

import com.ntoma.studio.data.local.db.DressStyleDao
import com.ntoma.studio.data.local.db.FabricDao
import com.ntoma.studio.data.local.db.GeneratedLookDao
import com.ntoma.studio.data.local.db.HistoryDao
import com.ntoma.studio.data.local.db.UsageDao
import com.ntoma.studio.data.local.db.UsageEntity
import com.ntoma.studio.data.mapper.toDomain
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.GeneratedLook
import com.ntoma.studio.domain.model.HistoryEvent
import com.ntoma.studio.domain.repository.EntitlementRepository
import com.ntoma.studio.domain.repository.FavoritesRepository
import com.ntoma.studio.domain.repository.HistoryRepository
import com.ntoma.studio.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar

class FavoritesRepositoryImpl(
    private val fabricDao: FabricDao,
    private val styleDao: DressStyleDao,
    private val lookDao: GeneratedLookDao,
) : FavoritesRepository {

    override fun observeFavoriteFabrics(): Flow<List<Fabric>> =
        fabricDao.observeFavorites().map { it.map(FabricEntityMapper) }

    override fun observeFavoriteDesigns(): Flow<List<DressStyle>> =
        styleDao.observeFavorites().map { it.map(StyleEntityMapper) }

    override fun observeFavoriteLooks(): Flow<List<GeneratedLook>> =
        lookDao.observeFavorites().map { it.map(LookEntityMapper) }

    override suspend fun isDesignFavorite(styleId: String): Boolean = styleDao.isFavorite(styleId) > 0

    override suspend fun setDesignFavorite(styleId: String, favorite: Boolean) {
        if (favorite) styleDao.addFavorite(com.ntoma.studio.data.local.db.FavoriteStyleEntity(styleId, System.currentTimeMillis()))
        else styleDao.removeFavorite(styleId)
    }

    override fun countsFlow(): Flow<Int> = kotlinx.coroutines.flow.combine(
        fabricDao.observeFavorites(),
        styleDao.observeFavorites(),
        lookDao.observeFavorites(),
    ) { f, d, l -> f.size + d.size + l.size }

    override suspend fun counts(): Triple<Int, Int, Int> = Triple(
        fabricDao.observeFavorites().first().size,
        styleDao.favoriteCount(),
        lookDao.observeFavorites().first().size,
    )

    private object FabricEntityMapper : (com.ntoma.studio.data.local.db.FabricEntity) -> Fabric {
        override fun invoke(e: com.ntoma.studio.data.local.db.FabricEntity): Fabric = e.toDomain()
    }

    private object StyleEntityMapper : (com.ntoma.studio.data.local.db.DressStyleEntity) -> DressStyle {
        override fun invoke(e: com.ntoma.studio.data.local.db.DressStyleEntity): DressStyle = e.toDomain()
    }

    private object LookEntityMapper : (com.ntoma.studio.data.local.db.GeneratedLookEntity) -> GeneratedLook {
        override fun invoke(e: com.ntoma.studio.data.local.db.GeneratedLookEntity): GeneratedLook = e.toDomain()
    }
}

class HistoryRepositoryImpl(
    private val historyDao: HistoryDao,
    private val settings: SettingsRepository,
) : HistoryRepository {

    override fun observeAll(): Flow<List<HistoryEvent>> =
        historyDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun record(event: HistoryEvent) {
        historyDao.insert(event.let {
            com.ntoma.studio.data.local.db.HistoryEntity(
                kind = it.kind.name,
                labelKey = it.labelKey,
                label = it.label,
                imageUri = it.imageUri,
                timestamp = it.timestamp,
            )
        })
        val days = settings.current().keepHistoryDays
        if (days > 0) pruneOlderThan(days)
    }

    override suspend fun delete(id: Long) = historyDao.delete(id)

    override suspend fun clear() = historyDao.clear()

    override suspend fun pruneOlderThan(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        historyDao.prune(cutoff)
    }
}

/** Freemium quota bookkeeping, stored in Room so it survives restarts and is testable. */
class EntitlementRepositoryImpl(
    private val usageDao: UsageDao,
    private val settings: SettingsRepository,
) : EntitlementRepository {

    companion object {
        const val FREE_ANALYSES_PER_DAY = 3
        const val FREE_TRYONS_PER_MONTH = 2
        const val UNLIMITED_SENTINEL = 999
    }

    private fun dayKey(): String {
        val c = Calendar.getInstance()
        return "analysis_%d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun monthKey(): String {
        val c = Calendar.getInstance()
        return "tryon_%d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    override fun remainingAnalysesToday(): Flow<Int> = settings.preferences.map { prefs ->
        if (prefs.premiumEnabled) UNLIMITED_SENTINEL
        else (FREE_ANALYSES_PER_DAY - (usageDao.byKey(dayKey())?.count ?: 0)).coerceAtLeast(0)
    }

    override fun remainingTryOnsThisMonth(): Flow<Int> = settings.preferences.map { prefs ->
        if (prefs.premiumEnabled) UNLIMITED_SENTINEL
        else (FREE_TRYONS_PER_MONTH - usedTryOns()).coerceAtLeast(0)
    }

    private suspend fun usedTryOns(): Int =
        (usageDao.byKey(monthKey())?.count ?: 0) - (usageDao.byKey(monthKey() + "_bonus")?.count ?: 0)

    override suspend fun canAnalyze(): Boolean =
        settings.current().premiumEnabled || (usageDao.byKey(dayKey())?.count ?: 0) < FREE_ANALYSES_PER_DAY

    override suspend fun canTryOn(): Boolean =
        settings.current().premiumEnabled || usedTryOns() < FREE_TRYONS_PER_MONTH

    override suspend fun consumeAnalysis() = bump(dayKey())

    override suspend fun consumeTryOn() = bump(monthKey())

    override suspend fun grantBonusTryOn() {
        val key = monthKey() + "_bonus"
        val current = usageDao.byKey(key)?.count ?: 0
        usageDao.upsert(UsageEntity(key, current + 1))
    }

    private suspend fun bump(key: String) {
        val current = usageDao.byKey(key)?.count ?: 0
        usageDao.upsert(UsageEntity(key, current + 1))
    }
}
