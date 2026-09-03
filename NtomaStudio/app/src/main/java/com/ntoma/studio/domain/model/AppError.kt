package com.ntoma.studio.domain.model

import com.ntoma.studio.R

/** Every failure the UI can surface, expressed as a stable domain type. */
sealed interface AppError {
    val titleRes: Int
    val bodyRes: Int

    data object Network : AppError {
        override val titleRes = R.string.error_no_internet_title
        override val bodyRes = R.string.error_no_internet_body
    }

    data object Server : AppError {
        override val titleRes = R.string.error_server_title
        override val bodyRes = R.string.error_server_body
    }

    data object Timeout : AppError {
        override val titleRes = R.string.error_timeout_title
        override val bodyRes = R.string.error_timeout_body
    }

    data object InvalidImage : AppError {
        override val titleRes = R.string.error_invalid_image_title
        override val bodyRes = R.string.error_invalid_image_body
    }

    data object UnsupportedImage : AppError {
        override val titleRes = R.string.error_unsupported_image_title
        override val bodyRes = R.string.error_unsupported_image_body
    }

    data object ImageTooLarge : AppError {
        override val titleRes = R.string.error_image_too_large_title
        override val bodyRes = R.string.error_image_too_large_body
    }

    data object Generation : AppError {
        override val titleRes = R.string.error_generation_title
        override val bodyRes = R.string.error_generation_body
    }

    data object RateLimit : AppError {
        override val titleRes = R.string.error_rate_limit_title
        override val bodyRes = R.string.error_rate_limit_body
    }

    data object Analysis : AppError {
        override val titleRes = R.string.error_analysis_title
        override val bodyRes = R.string.error_analysis_body
    }

    data object Permission : AppError {
        override val titleRes = R.string.error_permission_title
        override val bodyRes = R.string.error_generic_body
    }

    data object Memory : AppError {
        override val titleRes = R.string.error_memory_title
        override val bodyRes = R.string.error_memory_body
    }

    data object Unknown : AppError {
        override val titleRes = R.string.error_generic_title
        override val bodyRes = R.string.error_generic_body
    }
}

/** Small Result-style wrapper used across repositories so failures stay typed. */
sealed interface Outcome<out T> {
    data class Success<T>(val data: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    companion object {
        inline fun <T> runCatchingMapped(block: () -> T): Outcome<T> = try {
            Success(block())
        } catch (e: Exception) {
            Failure(AppError.Unknown)
        }
    }
}
