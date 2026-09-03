package com.ntoma.studio.data.repository

import android.net.Uri
import com.ntoma.studio.R
import com.ntoma.studio.analytics.AnalyticsLogger
import com.ntoma.studio.data.local.db.FabricDao
import com.ntoma.studio.data.ml.OnDeviceMlClassifier
import com.ntoma.studio.data.mapper.toDomain
import com.ntoma.studio.data.mapper.toEntity
import com.ntoma.studio.domain.model.AnalysisEngine
import com.ntoma.studio.domain.model.AppError
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.HistoryEvent
import com.ntoma.studio.domain.repository.AnalysisEvent
import com.ntoma.studio.domain.repository.FabricAnalysisRepository
import com.ntoma.studio.domain.repository.HistoryRepository
import com.ntoma.studio.media.BitmapAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * On-device demo analysis pipeline. Stages are paced so the process feels intentional; the heavy
 * pixel work happens off the main thread. Swap [BitmapAnalyzer] for a cloud client to go to
 * production without touching the UI.
 */
class FabricAnalysisRepositoryImpl(
    private val analyzer: BitmapAnalyzer,
    private val fabricDao: FabricDao,
    private val history: HistoryRepository,
    private val analytics: AnalyticsLogger,
    private val ml: OnDeviceMlClassifier,
    private val settings: com.ntoma.studio.domain.repository.SettingsRepository,
    private val cloud: com.ntoma.studio.data.remote.CloudAnalysisClient,
) : FabricAnalysisRepository {

    override fun analyze(imageUri: String): Flow<AnalysisEvent> = flow {
        emit(AnalysisEvent.Stage(R.string.analysis_stage_preparing))
        delay(STAGE_MS)
        emit(AnalysisEvent.Stage(R.string.analysis_stage_colors))
        delay(STAGE_MS)
        val signature = withContext(Dispatchers.Default) {
            try {
                analyzer.signature(Uri.parse(imageUri))
            } catch (oom: OutOfMemoryError) {
                null
            }
        }
        if (signature == null) {
            analytics.log("analysis_failed", mapOf("reason" to "invalid_image"))
            emit(AnalysisEvent.Failed(AppError.Analysis))
            return@flow
        }
        emit(AnalysisEvent.Stage(R.string.analysis_stage_pattern))
        delay(STAGE_MS)
        emit(AnalysisEvent.Stage(R.string.analysis_stage_styles))
        val hints = withContext(Dispatchers.Default) { ml.classify(Uri.parse(imageUri)) }

        // Optional cloud backend: overrides category/pattern/texture when an operator configured
        // one; any failure falls back to the on-device engine and is logged honestly.
        val prefs = settings.current()
        var verdict: com.ntoma.studio.data.remote.CloudVerdict? = null
        if (prefs.analysisEngine == AnalysisEngine.CLOUD && prefs.cloudBaseUrl.isNotBlank()) {
            val jpeg = withContext(Dispatchers.IO) { analyzer.jpegBytes(Uri.parse(imageUri)) }
            verdict = if (jpeg == null) null else withContext(Dispatchers.IO) {
                cloud.analyze(prefs.cloudBaseUrl, jpeg)
            }
            if (verdict == null) analytics.log("cloud_fallback", mapOf("reason" to "unreachable"))
        }

        delay(STAGE_MS)
        emit(AnalysisEvent.Stage(R.string.analysis_stage_finishing))
        delay(STAGE_MS / 2)
        analytics.log(
            "analysis_completed",
            mapOf(
                "category" to signature.category.name,
                "confidence" to "%.2f".format(signature.categoryConfidence),
                "pattern" to signature.pattern.name,
            ),
        )
        val local = analyzer.toFabric(signature, imageUri, AnalysisEngine.ON_DEVICE_DEMO)
        val fabric = if (verdict != null) {
            local.copy(
                category = verdict.category,
                confidence = verdict.confidence,
                pattern = verdict.pattern ?: local.pattern,
                texture = verdict.texture ?: local.texture,
                engine = AnalysisEngine.CLOUD,
                mlHints = hints,
            )
        } else {
            local.copy(mlHints = hints)
        }
        emit(AnalysisEvent.Completed(fabric))
    }

    override suspend fun save(fabric: Fabric): Long {
        val id = fabricDao.insert(fabric.toEntity())
        history.record(
            HistoryEvent(
                kind = HistoryEvent.Kind.ANALYSIS,
                label = fabric.name ?: fabric.category.name,
                labelKey = "fabric_${fabric.category.name.lowercase()}",
                imageUri = fabric.imageUri,
                timestamp = System.currentTimeMillis(),
            ),
        )
        return id
    }

    override suspend fun get(id: Long): Fabric? = fabricDao.byId(id)?.toDomain()

    override fun observeAll(): Flow<List<Fabric>> = fabricDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFavorites(): Flow<List<Fabric>> =
        fabricDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    override suspend fun rename(id: Long, name: String) = fabricDao.rename(id, name)

    override suspend fun setFavorite(id: Long, favorite: Boolean) = fabricDao.setFavorite(id, if (favorite) 1 else 0)

    override suspend fun updateDetails(
        id: Long,
        notes: String?,
        amountCm: Int?,
        intendedWearer: String?,
        intendedOccasion: String?,
    ) = fabricDao.updateDetails(id, notes, amountCm, intendedWearer, intendedOccasion)

    override suspend fun delete(id: Long) = fabricDao.delete(id)

    companion object {
        const val STAGE_MS = 380L
    }
}
