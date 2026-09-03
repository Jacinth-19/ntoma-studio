package com.ntoma.studio.domain.repository

import com.ntoma.studio.domain.model.AppError
import com.ntoma.studio.domain.model.GeneratedLook
import kotlinx.coroutines.flow.Flow

/**
 * The full state machine of a try-on run. The UI renders exactly these states, so a real AI
 * backend can stream genuine progress through the same flow.
 */
sealed interface TryOnState {
    data object Idle : TryOnState
    data object Uploading : TryOnState
    data object Processing : TryOnState
    data object Generating : TryOnState
    data object Finishing : TryOnState
    data class Success(val look: GeneratedLook) : TryOnState
    data class Error(val error: AppError) : TryOnState
}

data class TryOnRequest(
    val fabricId: Long,
    val dressStyleId: String,
    val personImageUri: String,
    val gender: GenderHint,
    val customization: com.ntoma.studio.domain.model.DesignCustomization = com.ntoma.studio.domain.model.DesignCustomization(),
    /** Deterministic seed for variation generation (0 = base look). */
    val variationSeed: Int = 0,
    /** Shared id linking variations of one request. */
    val groupId: String? = null,
)

enum class GenderHint { WOMAN, MAN, AUTO }

/**
 * Generates a look from a [TryOnRequest]. The bundled demo implementation composites on-device
 * and labels its output accordingly; a production virtual try-on API implements this interface.
 */
interface VirtualTryOnRepository {
    fun generate(request: TryOnRequest): Flow<TryOnState>
    fun observeLooks(): Flow<List<GeneratedLook>>
    fun observeFavoriteLooks(): Flow<List<GeneratedLook>>
    suspend fun getLook(id: Long): GeneratedLook?
    suspend fun setFavorite(id: Long, favorite: Boolean)
    suspend fun delete(id: Long)
}
