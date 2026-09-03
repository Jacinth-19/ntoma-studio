package com.ntoma.studio.data.remote

import com.ntoma.studio.R
import com.ntoma.studio.domain.model.GenderCategory
import com.ntoma.studio.domain.model.PriceRange
import com.ntoma.studio.domain.model.Tailor
import com.ntoma.studio.domain.repository.TailorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Clearly-labelled DEMO directory used until a real, verified tailor marketplace backend exists.
 * Every entry carries isDemo = true; the UI must show a demo banner and the quote flow is a
 * shareable brief — no transaction is ever implied.
 */
class DemoTailorSource : TailorRepository {

    private val demoTailors = listOf(
        Tailor(
            id = "demo-1",
            name = "Demo — Adinkra Stitches",
            isDemo = true,
            city = "Accra",
            area = "Osu",
            specialties = listOf(R.string.tailor_spec_kaba, R.string.tailor_spec_bridal),
            priceRange = PriceRange.MID,
            worksWith = GenderCategory.WOMEN,
            hoursSummary = R.string.tailor_hours_demo,
            contactSummary = "+233 20 000 0000 (demo)",
            verified = false,
            offersDelivery = true,
            portfolioNote = R.string.tailor_portfolio_demo,
        ),
        Tailor(
            id = "demo-2",
            name = "Demo — Golden Kaftan House",
            isDemo = true,
            city = "Kumasi",
            area = "Adum",
            specialties = listOf(R.string.tailor_spec_kaftan, R.string.tailor_spec_agbada),
            priceRange = PriceRange.PREMIUM,
            worksWith = GenderCategory.MEN,
            hoursSummary = R.string.tailor_hours_demo,
            contactSummary = "+233 24 000 0000 (demo)",
            verified = false,
            offersDelivery = false,
            portfolioNote = R.string.tailor_portfolio_demo,
        ),
        Tailor(
            id = "demo-3",
            name = "Demo — Smock & Tradition",
            isDemo = true,
            city = "Tamale",
            area = "Central",
            specialties = listOf(R.string.tailor_spec_smock),
            priceRange = PriceRange.BUDGET,
            worksWith = GenderCategory.UNISEX,
            hoursSummary = R.string.tailor_hours_demo,
            contactSummary = "+233 27 000 0000 (demo)",
            verified = false,
            offersDelivery = true,
            portfolioNote = R.string.tailor_portfolio_demo,
        ),
        Tailor(
            id = "demo-4",
            name = "Demo — Modern Kente Atelier",
            isDemo = true,
            city = "Accra",
            area = "East Legon",
            specialties = listOf(R.string.tailor_spec_kaba, R.string.tailor_spec_suits, R.string.tailor_spec_bridal),
            priceRange = PriceRange.PREMIUM,
            worksWith = GenderCategory.UNISEX,
            hoursSummary = R.string.tailor_hours_demo,
            contactSummary = "+233 55 000 0000 (demo)",
            verified = false,
            offersDelivery = true,
            portfolioNote = R.string.tailor_portfolio_demo,
        ),
    )

    private val flow = MutableStateFlow(demoTailors)

    override suspend fun all(): List<Tailor> = demoTailors

    override fun observeAll(): Flow<List<Tailor>> = flow
}
