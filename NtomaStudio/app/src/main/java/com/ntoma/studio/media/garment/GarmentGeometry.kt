package com.ntoma.studio.media.garment

import com.ntoma.studio.domain.model.GarmentSilhouette

/** A closed polygon of normalised (0..1) points. */
typealias Poly = List<Pair<Float, Float>>

/**
 * Normalised garment outlines (0..1 in both axes) shared by the Compose illustrations and the
 * demo try-on compositor, so a design always looks like itself everywhere.
 *
 * Each shape is a list of sub-paths; every sub-path is a closed polygon of points.
 */
object GarmentGeometry {

    fun shapesFor(silhouette: GarmentSilhouette): List<Poly> = when (silhouette) {
        GarmentSilhouette.KABA -> listOf(
            poly(0.32f, 0.06f, 0.5f, 0.02f, 0.68f, 0.06f, 0.74f, 0.3f, 0.62f, 0.34f, 0.66f, 0.98f, 0.34f, 0.98f, 0.38f, 0.34f, 0.26f, 0.3f),
        )
        GarmentSilhouette.MODERN_KABA -> listOf(
            poly(0.3f, 0.05f, 0.5f, 0.01f, 0.7f, 0.05f, 0.78f, 0.22f, 0.68f, 0.26f, 0.64f, 0.42f, 0.72f, 0.98f, 0.28f, 0.98f, 0.36f, 0.42f, 0.32f, 0.26f, 0.22f, 0.22f),
        )
        GarmentSilhouette.MAXI -> listOf(
            poly(0.34f, 0.04f, 0.5f, 0.0f, 0.66f, 0.04f, 0.7f, 0.26f, 0.6f, 0.3f, 0.8f, 0.98f, 0.2f, 0.98f, 0.4f, 0.3f, 0.3f, 0.26f),
        )
        GarmentSilhouette.STRAIGHT -> listOf(
            poly(0.34f, 0.04f, 0.5f, 0.0f, 0.66f, 0.04f, 0.7f, 0.3f, 0.66f, 0.96f, 0.34f, 0.96f, 0.3f, 0.3f),
        )
        GarmentSilhouette.MERMAID -> listOf(
            poly(0.36f, 0.03f, 0.5f, 0.0f, 0.64f, 0.03f, 0.66f, 0.3f, 0.6f, 0.6f, 0.58f, 0.74f, 0.76f, 0.98f, 0.24f, 0.98f, 0.42f, 0.74f, 0.4f, 0.6f, 0.34f, 0.3f),
        )
        GarmentSilhouette.PEPLUM -> listOf(
            poly(0.34f, 0.04f, 0.5f, 0.0f, 0.66f, 0.04f, 0.68f, 0.34f, 0.76f, 0.5f, 0.24f, 0.5f, 0.32f, 0.34f),
            poly(0.36f, 0.5f, 0.64f, 0.5f, 0.68f, 0.98f, 0.32f, 0.98f),
        )
        GarmentSilhouette.JUMPSUIT -> listOf(
            poly(0.34f, 0.04f, 0.5f, 0.0f, 0.66f, 0.04f, 0.68f, 0.42f, 0.7f, 0.98f, 0.54f, 0.98f, 0.5f, 0.56f, 0.46f, 0.98f, 0.3f, 0.98f, 0.32f, 0.42f),
        )
        GarmentSilhouette.SKIRT_BLOUSE -> listOf(
            poly(0.32f, 0.04f, 0.5f, 0.0f, 0.68f, 0.04f, 0.7f, 0.4f, 0.3f, 0.4f),
            poly(0.34f, 0.44f, 0.66f, 0.44f, 0.74f, 0.98f, 0.26f, 0.98f),
        )
        GarmentSilhouette.GOWN -> listOf(
            poly(0.36f, 0.03f, 0.5f, 0.0f, 0.64f, 0.03f, 0.66f, 0.26f, 0.58f, 0.32f, 0.86f, 0.98f, 0.14f, 0.98f, 0.42f, 0.32f, 0.34f, 0.26f),
        )
        GarmentSilhouette.FORMAL_DRESS -> listOf(
            poly(0.35f, 0.04f, 0.5f, 0.0f, 0.65f, 0.04f, 0.68f, 0.32f, 0.62f, 0.92f, 0.38f, 0.92f, 0.32f, 0.32f),
        )
        GarmentSilhouette.KAFTAN -> listOf(
            poly(0.34f, 0.04f, 0.5f, 0.0f, 0.66f, 0.04f, 0.76f, 0.5f, 0.7f, 0.98f, 0.3f, 0.98f, 0.24f, 0.5f),
            poly(0.46f, 0.08f, 0.54f, 0.08f, 0.54f, 0.3f, 0.46f, 0.3f),
        )
        GarmentSilhouette.AGBADA -> listOf(
            poly(0.36f, 0.03f, 0.5f, 0.0f, 0.64f, 0.03f, 0.92f, 0.5f, 0.74f, 0.56f, 0.76f, 0.98f, 0.24f, 0.98f, 0.26f, 0.56f, 0.08f, 0.5f),
        )
        GarmentSilhouette.PRINT_SHIRT -> listOf(
            poly(0.32f, 0.06f, 0.5f, 0.0f, 0.68f, 0.06f, 0.78f, 0.3f, 0.68f, 0.34f, 0.68f, 0.62f, 0.32f, 0.62f, 0.32f, 0.34f, 0.22f, 0.3f),
        )
        GarmentSilhouette.SENATOR -> listOf(
            poly(0.34f, 0.04f, 0.5f, 0.0f, 0.66f, 0.04f, 0.74f, 0.42f, 0.68f, 0.46f, 0.66f, 0.86f, 0.34f, 0.86f, 0.32f, 0.46f, 0.26f, 0.42f),
        )
        GarmentSilhouette.SMOCK -> listOf(
            poly(0.3f, 0.08f, 0.5f, 0.0f, 0.7f, 0.08f, 0.82f, 0.6f, 0.7f, 0.98f, 0.3f, 0.98f, 0.18f, 0.6f),
            poly(0.42f, 0.06f, 0.58f, 0.06f, 0.58f, 0.16f, 0.42f, 0.16f),
        )
        GarmentSilhouette.AFRICAN_SUIT -> listOf(
            poly(0.3f, 0.05f, 0.48f, 0.0f, 0.48f, 0.52f, 0.32f, 0.52f, 0.32f, 0.3f, 0.22f, 0.28f),
            poly(0.7f, 0.05f, 0.52f, 0.0f, 0.52f, 0.52f, 0.68f, 0.52f, 0.68f, 0.3f, 0.78f, 0.28f),
            poly(0.34f, 0.56f, 0.48f, 0.56f, 0.48f, 0.98f, 0.34f, 0.98f),
            poly(0.52f, 0.56f, 0.66f, 0.56f, 0.66f, 0.98f, 0.52f, 0.98f),
        )
        GarmentSilhouette.SHIRT_TROUSERS -> listOf(
            poly(0.32f, 0.04f, 0.5f, 0.0f, 0.68f, 0.04f, 0.74f, 0.26f, 0.66f, 0.3f, 0.66f, 0.48f, 0.34f, 0.48f, 0.34f, 0.3f, 0.26f, 0.26f),
            poly(0.36f, 0.52f, 0.48f, 0.52f, 0.47f, 0.98f, 0.36f, 0.98f),
            poly(0.52f, 0.52f, 0.64f, 0.52f, 0.64f, 0.98f, 0.53f, 0.98f),
        )
        GarmentSilhouette.MODERN_TRADITIONAL -> listOf(
            poly(0.34f, 0.04f, 0.5f, 0.0f, 0.66f, 0.04f, 0.74f, 0.36f, 0.66f, 0.4f, 0.64f, 0.9f, 0.36f, 0.9f, 0.34f, 0.4f, 0.26f, 0.36f),
            poly(0.44f, 0.04f, 0.56f, 0.04f, 0.56f, 0.2f, 0.44f, 0.2f),
        )
        GarmentSilhouette.COVER_UP -> listOf(
            poly(0.3f, 0.06f, 0.5f, 0.0f, 0.7f, 0.06f, 0.84f, 0.44f, 0.72f, 0.5f, 0.74f, 0.96f, 0.26f, 0.96f, 0.28f, 0.5f, 0.16f, 0.44f),
        )
        GarmentSilhouette.CO_ORDS -> listOf(
            poly(0.32f, 0.04f, 0.5f, 0.0f, 0.68f, 0.04f, 0.72f, 0.28f, 0.64f, 0.32f, 0.64f, 0.5f, 0.36f, 0.5f, 0.36f, 0.32f, 0.28f, 0.28f),
            poly(0.34f, 0.54f, 0.66f, 0.54f, 0.7f, 0.86f, 0.54f, 0.86f, 0.5f, 0.7f, 0.46f, 0.86f, 0.3f, 0.86f),
        )
    }

    private fun poly(vararg coords: Float): Poly {
        require(coords.size % 2 == 0)
        return coords.toList().chunked(2).map { it[0] to it[1] }
    }
}
