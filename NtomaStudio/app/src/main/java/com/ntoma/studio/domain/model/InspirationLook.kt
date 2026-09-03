package com.ntoma.studio.domain.model

/**
 * A curated catalog reference photo of a real outfit, tagged to fabric categories and gender.
 * Honest by design: these are reference photos from the bundled catalog, never presented as
 * a render of the user's own fabric.
 */
data class InspirationLook(
    val id: String,
    val titleKey: String,
    val gender: GenderCategory,
    val fabrics: List<FabricCategory>,
    val asset: String,
) {
    companion object {
        /** Pure matcher, unit-tested: a look matches when fabric category is in its tags and the
         *  gender filter is either unset or satisfied. */
        fun matches(look: InspirationLook, fabricCategory: FabricCategory?, gender: GenderCategory?): Boolean {
            val fabricOk = fabricCategory == null || fabricCategory == FabricCategory.UNKNOWN ||
                fabricCategory in look.fabrics
            val genderOk = gender == null || look.gender == gender
            return fabricOk && genderOk
        }
    }
}
