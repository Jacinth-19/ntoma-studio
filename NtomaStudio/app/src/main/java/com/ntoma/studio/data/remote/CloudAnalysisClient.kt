package com.ntoma.studio.data.remote

import com.ntoma.studio.domain.model.AnalysisEngine
import com.ntoma.studio.domain.model.FabricCategory
import com.ntoma.studio.domain.model.PatternType
import com.ntoma.studio.domain.model.TextureType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Optional cloud CV backend. Dormant until an operator sets a base URL in settings; the app
 * never ships a hidden endpoint and the default engine stays on-device.
 *
 * Expected server contract (documented for whoever stands the backend up):
 *   POST {base}/v1/analyze   multipart "image" (jpeg) -> JSON RemoteAnalysisDto
 */
interface AnalysisApi {
    @Multipart
    @POST("v1/analyze")
    suspend fun analyze(@Part image: MultipartBody.Part): retrofit2.Response<RemoteAnalysisDto>
}

data class RemoteAnalysisDto(
    val category: String? = null,
    val confidence: Float = 0f,
    val pattern: String? = null,
    val texture: String? = null,
)

/** Result of a remote analysis; mapped into the domain with caps so honesty holds. */
data class CloudVerdict(
    val category: FabricCategory,
    val confidence: Float,
    val pattern: PatternType?,
    val texture: TextureType?,
)

class CloudAnalysisClient {

    private var api: AnalysisApi? = null
    private var builtFor: String? = null

    private fun apiFor(baseUrl: String): AnalysisApi {
        api?.let { if (builtFor == baseUrl) return it }
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(AnalysisApi::class.java).also {
            api = it
            builtFor = baseUrl
        }
    }

    /** Null when no backend is configured or the call fails — caller falls back to on-device. */
    suspend fun analyze(baseUrl: String, jpeg: ByteArray): CloudVerdict? {
      return try {
        val body = jpeg.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("image", "fabric.jpg", body)
        val response = apiFor(baseUrl).analyze(part)
        if (!response.isSuccessful) return null
        val dto = response.body() ?: return null
        CloudVerdict(
            category = FabricCategory.values()
                .firstOrNull { it.name.equals(dto.category, ignoreCase = true) }
                ?: FabricCategory.UNKNOWN,
            confidence = dto.confidence.coerceIn(0f, 0.8f), // never overstate, even remotely
            pattern = PatternType.values().firstOrNull { it.name.equals(dto.pattern, ignoreCase = true) },
            texture = TextureType.values().firstOrNull { it.name.equals(dto.texture, ignoreCase = true) },
        )
      } catch (t: Throwable) {
        null
      }
    }

    fun engineFor(baseUrl: String): AnalysisEngine =
        if (baseUrl.isBlank()) AnalysisEngine.ON_DEVICE_DEMO else AnalysisEngine.CLOUD
}
