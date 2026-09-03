package com.ntoma.studio.ui.screens.create

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.model.AppError
import com.ntoma.studio.domain.model.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

enum class CropAspect(val ratio: Float?, val labelRes: Int) {
    FREE(null, com.ntoma.studio.R.string.edit_ratio_free),
    SQUARE(1f, com.ntoma.studio.R.string.edit_ratio_square),
    PORTRAIT(3f / 4f, com.ntoma.studio.R.string.edit_ratio_portrait),
    FULL(null, com.ntoma.studio.R.string.edit_ratio_full),
}

data class ImageEditUiState(
    val bitmap: Bitmap? = null,
    val rotation: Int = 0,
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val aspect: CropAspect = CropAspect.FREE,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: AppError? = null,
    val resultPath: String? = null,
)

class ImageEditViewModel(
    private val container: AppContainer,
    private val uri: String,
    private val target: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ImageEditUiState())
    val state: StateFlow<ImageEditUiState> = _state.asStateFlow()

    var viewW = 0
    var viewH = 0

    init {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                container.imageProcessor.load(Uri.parse(uri), com.ntoma.studio.media.ImageProcessor.EDIT_MAX_EDGE)
            }
            when (outcome) {
                is Outcome.Success -> _state.value = _state.value.copy(bitmap = outcome.data, loading = false)
                is Outcome.Failure -> _state.value = _state.value.copy(error = outcome.error, loading = false)
            }
        }
    }

    fun rotate() {
        _state.value = _state.value.copy(rotation = (_state.value.rotation + 90) % 360)
    }

    fun reset() {
        _state.value = _state.value.copy(rotation = 0, scale = 1f, panX = 0f, panY = 0f)
    }

    fun setAspect(aspect: CropAspect) {
        _state.value = _state.value.copy(aspect = aspect)
    }

    fun onGesture(zoom: Float, dx: Float, dy: Float) {
        val s = _state.value
        _state.value = s.copy(
            scale = (s.scale * zoom).coerceIn(1f, 6f),
            panX = s.panX + dx,
            panY = s.panY + dy,
        )
    }

    fun confirm() {
        val s = _state.value
        val bmp = s.bitmap ?: return
        if (viewW == 0 || viewH == 0) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                try {
                    val cover = max(viewW.toFloat() / bmp.width, viewH.toFloat() / bmp.height)
                    val transformed = container.imageProcessor.transform(
                        source = bmp,
                        rotationDeg = s.rotation,
                        scale = s.scale,
                        panX = s.panX,
                        panY = s.panY,
                        viewW = viewW,
                        viewH = viewH,
                        coverScale = cover,
                    )
                    val cropped = cropToAspect(transformed, s.aspect)
                    container.imageProcessor.save(cropped, if (target == "person") "person" else "fabric")
                } catch (oom: OutOfMemoryError) {
                    null
                }
            }
            _state.value = _state.value.copy(
                saving = false,
                resultPath = result?.absolutePath,
                error = if (result == null) AppError.Memory else null,
            )
        }
    }

    private fun cropToAspect(bitmap: Bitmap, aspect: CropAspect): Bitmap {
        val ratio = aspect.ratio ?: return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val (cw, ch) = if (w.toFloat() / h > ratio) {
            (h * ratio).toInt() to h
        } else {
            w to (w / ratio).toInt()
        }
        val x = (w - cw) / 2
        val y = (h - ch) / 2
        return try {
            Bitmap.createBitmap(bitmap, x, y, cw.coerceAtLeast(1), ch.coerceAtLeast(1))
        } catch (e: Exception) {
            bitmap
        }
    }
}
