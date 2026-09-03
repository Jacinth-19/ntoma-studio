package com.ntoma.studio.domain.model

data class HistoryEvent(
    val id: Long = 0,
    val kind: Kind,
    /** A resolvable string-resource key such as "style_kaba_slit"; null when [label] suffices. */
    val labelKey: String? = null,
    /** Plain-text label (user-provided names, fabric categories). */
    val label: String? = null,
    val imageUri: String? = null,
    val timestamp: Long,
) {
    enum class Kind { SCAN, ANALYSIS, DESIGN_OPEN, LOOK_CREATED }
}
