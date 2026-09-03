package com.ntoma.studio.domain.model

/**
 * The result of the virtual try-on workflow.
 *
 * [resultImageUri] points at a file in app-private storage so it survives process death and can
 * be shared through the FileProvider.
 */
data class GeneratedLook(
    val id: Long = 0,
    val fabricId: Long,
    val dressStyleId: String,
    val personImageUri: String,
    val resultImageUri: String,
    val engine: TryOnEngine,
    val isFavorite: Boolean = false,
    val createdAt: Long,
    /** Links variations generated from the same request. */
    val variationGroup: String? = null,
)

enum class TryOnEngine {
    /** On-device compositing demo: colour + scale only. Clearly badged as such. */
    DEMO_COMPOSITE,

    /** A real AI virtual try-on backend, once connected. */
    AI_GENERATIVE,
}
