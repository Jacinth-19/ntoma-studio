package com.ntoma.studio.data.mapper

import com.ntoma.studio.data.local.db.DressStyleEntity
import com.ntoma.studio.data.local.db.FabricEntity
import com.ntoma.studio.data.local.db.GeneratedLookEntity
import com.ntoma.studio.data.local.db.HistoryEntity
import com.ntoma.studio.domain.model.AnalyzedColor
import com.ntoma.studio.domain.model.AnalysisEngine
import com.ntoma.studio.domain.model.ColorName
import com.ntoma.studio.domain.model.DressStyle
import com.ntoma.studio.domain.model.Fabric
import com.ntoma.studio.domain.model.FabricCategory
import com.ntoma.studio.domain.model.GarmentSilhouette
import com.ntoma.studio.domain.model.GeneratedLook
import com.ntoma.studio.domain.model.GenderCategory
import com.ntoma.studio.domain.model.HistoryEvent
import com.ntoma.studio.domain.model.Occasion
import com.ntoma.studio.domain.model.PaletteSummary
import com.ntoma.studio.domain.model.PatternType
import com.ntoma.studio.domain.model.StyleCategory
import com.ntoma.studio.domain.model.TextureType
import com.ntoma.studio.domain.model.TryOnEngine

internal fun List<String>.toCsv(): String = joinToString(",")
internal fun String.fromCsv(): List<String> = split(',').filter { it.isNotBlank() }

internal fun <E : Enum<E>> String.enumOrDefault(values: Array<E>, default: E): E =
    values.firstOrNull { it.name == this } ?: default

fun Fabric.toEntity(): FabricEntity = FabricEntity(
    id = id,
    imageUri = imageUri,
    name = name,
    category = category.name,
    colorsJson = colors.joinToString(";") { "${it.argb}|${it.colorName.ordinal}|${"%.3f".format(it.fraction)}" },
    pattern = pattern.name,
    texture = texture.name,
    confidence = confidence,
    saturation = palette.saturation,
    brightness = palette.brightness,
    contrast = palette.contrast,
    colorCount = palette.colorCount,
    motifScale = motifScale,
    printTraits = printCharacteristics.joinToString(","),
    suggestedUses = suggestedUses.map { it.name }.toCsv(),
    engine = engine.name,
    isFavorite = if (isFavorite) 1 else 0,
    createdAt = createdAt,
    notes = notes,
    amountCm = amountCm,
    intendedWearer = intendedWearer,
    intendedOccasion = intendedOccasion,
    acquiredAt = acquiredAt,
)

fun FabricEntity.toDomain(): Fabric = Fabric(
    id = id,
    imageUri = imageUri,
    name = name,
    category = category.enumOrDefault(FabricCategory.values(), FabricCategory.UNKNOWN),
    colors = colorsJson.split(';').filter { it.isNotBlank() }.mapNotNull { part ->
        val bits = part.split('|')
        if (bits.size != 3) null else AnalyzedColor(
            argb = bits[0].toLongOrNull() ?: return@mapNotNull null,
            colorName = ColorName.values().getOrNull(bits[1].toIntOrNull() ?: 0) ?: ColorName.GREY,
            fraction = bits[2].toFloatOrNull() ?: 0f,
        )
    },
    pattern = pattern.enumOrDefault(PatternType.values(), PatternType.ABSTRACT),
    texture = texture.enumOrDefault(TextureType.values(), TextureType.WOVEN),
    confidence = confidence,
    palette = PaletteSummary(saturation, brightness, contrast, colorCount),
    motifScale = motifScale,
    printCharacteristics = printTraits.fromCsv().mapNotNull { it.toIntOrNull() },
    suggestedUses = suggestedUses.fromCsv().map { it.enumOrDefault(Occasion.values(), Occasion.EVERYDAY) },
    engine = engine.enumOrDefault(AnalysisEngine.values(), AnalysisEngine.ON_DEVICE_DEMO),
    isFavorite = isFavorite == 1,
    createdAt = createdAt,
    notes = notes,
    amountCm = amountCm,
    intendedWearer = intendedWearer,
    intendedOccasion = intendedOccasion,
    acquiredAt = acquiredAt,
)

fun DressStyle.toEntity(): DressStyleEntity = DressStyleEntity(
    id = id,
    titleKey = titleKey,
    descriptionKey = descriptionKey,
    gender = gender.name,
    category = category.name,
    occasions = occasions.map { it.name }.toCsv(),
    silhouette = silhouette.name,
    affinity = affinity.map { it.name }.toCsv(),
    structure = structure,
)

fun DressStyleEntity.toDomain(): DressStyle = DressStyle(
    id = id,
    titleKey = titleKey,
    descriptionKey = descriptionKey,
    gender = gender.enumOrDefault(GenderCategory.values(), GenderCategory.UNISEX),
    category = category.enumOrDefault(StyleCategory.values(), StyleCategory.CASUAL),
    occasions = occasions.fromCsv().map { it.enumOrDefault(Occasion.values(), Occasion.EVERYDAY) }.toSet(),
    silhouette = silhouette.enumOrDefault(GarmentSilhouette.values(), GarmentSilhouette.STRAIGHT),
    affinity = affinity.fromCsv().map { it.enumOrDefault(FabricCategory.values(), FabricCategory.UNKNOWN) }.toSet(),
    structure = structure,
)

fun GeneratedLook.toEntity(): GeneratedLookEntity = GeneratedLookEntity(
    id = id,
    fabricId = fabricId,
    dressStyleId = dressStyleId,
    personImageUri = personImageUri,
    resultImageUri = resultImageUri,
    engine = engine.name,
    isFavorite = if (isFavorite) 1 else 0,
    createdAt = createdAt,
    variationGroup = variationGroup,
)

fun GeneratedLookEntity.toDomain(): GeneratedLook = GeneratedLook(
    id = id,
    fabricId = fabricId,
    dressStyleId = dressStyleId,
    personImageUri = personImageUri,
    resultImageUri = resultImageUri,
    engine = engine.enumOrDefault(TryOnEngine.values(), TryOnEngine.DEMO_COMPOSITE),
    isFavorite = isFavorite == 1,
    createdAt = createdAt,
    variationGroup = variationGroup,
)

fun HistoryEvent.toEntity(): HistoryEntity = HistoryEntity(
    id = id,
    kind = kind.name,
    labelKey = labelKey,
    label = label,
    imageUri = imageUri,
    timestamp = timestamp,
)

fun HistoryEntity.toDomain(): HistoryEvent = HistoryEvent(
    id = id,
    kind = kind.enumOrDefault(HistoryEvent.Kind.values(), HistoryEvent.Kind.SCAN),
    labelKey = labelKey,
    label = label,
    imageUri = imageUri,
    timestamp = timestamp,
)
