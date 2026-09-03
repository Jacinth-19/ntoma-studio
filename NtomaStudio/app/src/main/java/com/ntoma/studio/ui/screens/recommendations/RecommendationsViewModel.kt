package com.ntoma.studio.ui.screens.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ntoma.studio.di.AppContainer
import com.ntoma.studio.domain.engine.RecommendationEngine
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.GenderCategory
import com.ntoma.studio.domain.model.Occasion
import com.ntoma.studio.domain.model.Recommendation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class RecsUiState(
    val fabric: Fabric? = null,
    val looks: List<com.ntoma.studio.domain.model.InspirationLook> = emptyList(),
    val filtered: List<Recommendation> = emptyList(),
    val gender: GenderCategory? = null,
    val occasions: Set<Occasion> = emptySet(),
    val favoriteStyles: Set<String> = emptySet(),
    val loaded: Boolean = false,
)

class RecommendationsViewModel(
    private val container: AppContainer,
    private val fabricId: Long,
) : ViewModel() {

    private val allRecs = MutableStateFlow<List<Recommendation>>(emptyList())
    private val fabricFlow = MutableStateFlow<Fabric?>(null)
    private val genderFlow = MutableStateFlow<GenderCategory?>(null)
    private val occasionsFlow = MutableStateFlow<Set<Occasion>>(emptySet())
    private val favoritesFlow = MutableStateFlow<Set<String>>(emptySet())
    private val dismissedFlow = MutableStateFlow<Set<String>>(emptySet())
    private val allLooksFlow = MutableStateFlow<List<com.ntoma.studio.domain.model.InspirationLook>>(emptyList())

    private val _state = MutableStateFlow(RecsUiState())
    val state: StateFlow<RecsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val fabric = container.fabricAnalysis.get(fabricId)
            fabricFlow.value = fabric
            if (fabric != null) {
                allRecs.value = RecommendationEngine.recommend(fabric, container.dressStyles.all())
            }
        }
        viewModelScope.launch {
            allLooksFlow.value = try {
                container.catalog.fetchInspirationLooks()
            } catch (_: Exception) {
                emptyList()
            }
        }
        viewModelScope.launch {
            container.favorites.observeFavoriteDesigns().collect { favs ->
                favoritesFlow.value = favs.map { it.id }.toSet()
            }
        }
        viewModelScope.launch {
            container.feedback.observeDismissed().collect { dismissed ->
                dismissedFlow.value = dismissed
            }
        }
        viewModelScope.launch {
            combine(allRecs, fabricFlow, genderFlow, occasionsFlow, favoritesFlow, dismissedFlow, allLooksFlow) { values ->
                @Suppress("UNCHECKED_CAST")
                val recs = values[0] as List<Recommendation>
                val fabric = values[1] as Fabric?
                val gender = values[2] as GenderCategory?
                val occasions = values[3] as Set<Occasion>
                val favs = values[4] as Set<String>
                @Suppress("UNCHECKED_CAST")
                val dismissed = values[5] as Set<String>
                @Suppress("UNCHECKED_CAST")
                val allLooks = values[6] as List<com.ntoma.studio.domain.model.InspirationLook>
                RecsUiState(
                    fabric = fabric,
                    looks = allLooks.filter { look ->
                        com.ntoma.studio.domain.model.InspirationLook.matches(look, fabric?.category, gender)
                    },
                    loaded = fabric != null,
                    gender = gender,
                    occasions = occasions,
                    favoriteStyles = favs,
                    filtered = recs.filter { rec ->
                        rec.style.id !in dismissed &&
                            (gender == null || rec.style.gender == gender || rec.style.gender == GenderCategory.UNISEX) &&
                            (occasions.isEmpty() || rec.style.occasions.intersect(occasions).isNotEmpty())
                    },
                )
            }.collect { _state.value = it }
        }
    }

    fun setGender(gender: GenderCategory) {
        genderFlow.value = if (genderFlow.value == gender) null else gender
    }

    fun toggleOccasion(occ: Occasion) {
        val current = occasionsFlow.value
        occasionsFlow.value = if (occ in current) current - occ else current + occ
    }

    fun clearFilters() {
        genderFlow.value = null
        occasionsFlow.value = emptySet()
    }

    fun toggleFavorite(style: DressStyle) {
        viewModelScope.launch {
            val now = !favoritesFlow.value.contains(style.id)
            container.favorites.setDesignFavorite(style.id, now)
        }
    }

    fun dismiss(style: DressStyle) {
        viewModelScope.launch { container.feedback.dismiss(style.id) }
    }

    fun undoDismiss(style: DressStyle) {
        viewModelScope.launch { container.feedback.undo(style.id) }
    }
}
