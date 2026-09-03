package com.ntoma.studio.ui.util

import android.content.Context
import com.ntoma.studio.R
import com.ntoma.studio.domain.model.WhyReason

/** Localized rendering of recommendation reasons. */
fun Context.whyText(reason: WhyReason): String = when (reason) {
    WhyReason.PatternStructured -> getString(R.string.why_pattern_structured)
    WhyReason.PatternFlowing -> getString(R.string.why_pattern_flowing)
    WhyReason.ScaleLarge -> getString(R.string.why_scale_large)
    WhyReason.ScaleSmall -> getString(R.string.why_scale_small)
    WhyReason.TextureHeavy -> getString(R.string.why_texture_heavy)
    WhyReason.TextureLight -> getString(R.string.why_texture_light)
    WhyReason.ColorsRich -> getString(R.string.why_colors_rich)
    WhyReason.ColorsMuted -> getString(R.string.why_colors_muted)
    is WhyReason.OccasionFit -> getString(R.string.why_occasion, getString(reason.occasion.labelRes))
    WhyReason.Tradition -> getString(R.string.why_tradition)
    is WhyReason.GenderFit -> getString(R.string.why_gender, getString(reason.gender.labelRes))
}
