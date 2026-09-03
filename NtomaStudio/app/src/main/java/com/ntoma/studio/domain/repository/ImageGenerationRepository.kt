package com.ntoma.studio.domain.repository

import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.Outcome
import java.io.File

data class GenerationInput(
    val fabric: Fabric,
    val style: DressStyle,
    val personImagePath: String,
    val customization: com.ntoma.studio.domain.model.DesignCustomization = com.ntoma.studio.domain.model.DesignCustomization(),
    val variationSeed: Int = 0,
    /** Honours the Data Saver setting: smaller working images mean less memory and storage. */
    val maxDimension: Int = 1000,
)

/**
 * Produces the result image of a try-on. The bundled demo implementation composites on-device;
 * a production virtual try-on / diffusion backend implements the same contract.
 */
interface ImageGenerationRepository {
    suspend fun generate(input: GenerationInput): Outcome<File>
}
