package com.ntoma.studio.data.repository

import com.ntoma.studio.analytics.AnalyticsLogger
import com.ntoma.studio.data.local.db.DressStyleDao
import com.ntoma.studio.data.local.db.FabricDao
import com.ntoma.studio.data.local.db.GeneratedLookDao
import com.ntoma.studio.data.mapper.toDomain
import com.ntoma.studio.data.mapper.toEntity
import com.ntoma.studio.domain.model.GeneratedLook
import com.ntoma.studio.domain.model.HistoryEvent
import com.ntoma.studio.domain.model.Outcome
import com.ntoma.studio.domain.model.TryOnEngine
import com.ntoma.studio.domain.repository.HistoryRepository
import com.ntoma.studio.domain.repository.ImageGenerationRepository
import com.ntoma.studio.domain.repository.TryOnRequest
import com.ntoma.studio.domain.repository.TryOnState
import com.ntoma.studio.domain.repository.VirtualTryOnRepository
import com.ntoma.studio.notifications.NtomaNotifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Drives the try-on state machine. The demo provider streams believable stages and produces a
 * composited preview; a production backend can stream real progress through the same states.
 */
class VirtualTryOnRepositoryImpl(
    private val lookDao: GeneratedLookDao,
    private val fabricDao: FabricDao,
    private val styleDao: DressStyleDao,
    private val generator: ImageGenerationRepository,
    private val history: HistoryRepository,
    private val notifier: NtomaNotifier,
    private val analytics: AnalyticsLogger,
    private val settings: com.ntoma.studio.domain.repository.SettingsRepository,
) : VirtualTryOnRepository {

    override fun generate(request: TryOnRequest): Flow<TryOnState> = flow {
        emit(TryOnState.Uploading)
        delay(450)
        val fabric = fabricDao.byId(request.fabricId)?.toDomain()
        val style = styleDao.byId(request.dressStyleId)?.toDomain()
        if (fabric == null || style == null) {
            emit(TryOnState.Error(com.ntoma.studio.domain.model.AppError.InvalidImage))
            return@flow
        }
        emit(TryOnState.Processing)
        delay(500)
        emit(TryOnState.Generating)
        val outcome = generator.generate(
            com.ntoma.studio.domain.repository.GenerationInput(
                fabric = fabric,
                style = style,
                personImagePath = request.personImageUri,
                customization = request.customization,
                variationSeed = request.variationSeed,
                maxDimension = if (settings.current().dataSaver) 720 else 1000,
            ),
        )
        when (outcome) {
            is Outcome.Failure -> {
                analytics.log("tryon_failed", mapOf("reason" to outcome.error.toString()))
                emit(TryOnState.Error(outcome.error))
            }
            is Outcome.Success -> {
                emit(TryOnState.Finishing)
                delay(400)
                val id = lookDao.insert(
                    GeneratedLook(
                        fabricId = request.fabricId,
                        dressStyleId = request.dressStyleId,
                        personImageUri = request.personImageUri,
                        resultImageUri = outcome.data.absolutePath,
                        engine = TryOnEngine.DEMO_COMPOSITE,
                        createdAt = System.currentTimeMillis(),
                        variationGroup = request.groupId,
                    ).toEntity(),
                )
                val look = lookDao.byId(id)!!.toDomain()
                history.record(
                    HistoryEvent(
                        kind = HistoryEvent.Kind.LOOK_CREATED,
                        labelKey = style.titleKey,
                        imageUri = look.resultImageUri,
                        timestamp = System.currentTimeMillis(),
                    ),
                )
                analytics.log("tryon_completed", mapOf("style" to style.id, "engine" to "demo"))
                notifier.lookReady(styleTitleKey = style.titleKey, lookId = look.id)
                emit(TryOnState.Success(look))
            }
        }
    }

    override fun observeLooks(): Flow<List<GeneratedLook>> =
        lookDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeFavoriteLooks(): Flow<List<GeneratedLook>> =
        lookDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    override suspend fun getLook(id: Long): GeneratedLook? = lookDao.byId(id)?.toDomain()

    override suspend fun setFavorite(id: Long, favorite: Boolean) = lookDao.setFavorite(id, if (favorite) 1 else 0)

    override suspend fun delete(id: Long) = lookDao.delete(id)
}
