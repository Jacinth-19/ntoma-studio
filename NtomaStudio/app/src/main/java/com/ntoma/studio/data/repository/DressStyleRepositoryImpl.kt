package com.ntoma.studio.data.repository

import com.ntoma.studio.data.local.db.DressStyleDao
import com.ntoma.studio.data.mapper.toDomain
import com.ntoma.studio.data.remote.CatalogDataSource
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Outcome
import com.ntoma.studio.domain.repository.DressStyleRepository
import com.ntoma.studio.data.local.db.DressStyleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room-backed catalogue, seeded lazily from the bundled asset (the stand-in backend). */
class DressStyleRepositoryImpl(
    private val dao: DressStyleDao,
    private val source: CatalogDataSource,
) : DressStyleRepository {

    private val seedMutex = Mutex()

    private suspend fun ensureSeeded() = seedMutex.withLock {
        if (dao.count() == 0) {
            try {
                dao.upsertAll(source.fetchStyles())
            } catch (e: Exception) {
                // Catalogue stays empty; UIs show friendly empty states.
            }
        }
    }

    override fun observeAll(): Flow<List<DressStyle>> =
        kotlinx.coroutines.flow.flow<List<DressStyleEntity>> {
            ensureSeeded()
            emitAll(dao.observeAll())
        }.map { list -> list.map { it.toDomain() } }

    override suspend fun all(): List<DressStyle> {
        ensureSeeded()
        return dao.all().map { it.toDomain() }
    }

    override suspend fun byId(id: String): DressStyle? {
        ensureSeeded()
        return dao.byId(id)?.toDomain()
    }

    override suspend fun refresh(): Outcome<Int> = try {
        val styles = source.fetchStyles()
        dao.upsertAll(styles)
        Outcome.Success(styles.size)
    } catch (e: Exception) {
        Outcome.Failure(com.ntoma.studio.domain.model.AppError.Server)
    }
}
