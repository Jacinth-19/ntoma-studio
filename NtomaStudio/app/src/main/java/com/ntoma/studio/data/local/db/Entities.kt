package com.ntoma.studio.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fabrics")
data class FabricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String,
    val name: String?,
    val category: String,
    val colorsJson: String,
    val pattern: String,
    val texture: String,
    val confidence: Float,
    val saturation: Float,
    val brightness: Float,
    val contrast: Float,
    val colorCount: Int,
    val motifScale: Float,
    val printTraits: String,
    val suggestedUses: String,
    val engine: String,
    val isFavorite: Int,
    val createdAt: Long,
    val notes: String? = null,
    val amountCm: Int? = null,
    val intendedWearer: String? = null,
    val intendedOccasion: String? = null,
    val acquiredAt: Long? = null,
)

@Entity(tableName = "dress_styles")
data class DressStyleEntity(
    @PrimaryKey val id: String,
    val titleKey: String,
    val descriptionKey: String,
    val gender: String,
    val category: String,
    val occasions: String,
    val silhouette: String,
    val affinity: String,
    val structure: Float,
)

@Entity(tableName = "favorite_styles")
data class FavoriteStyleEntity(
    @PrimaryKey val styleId: String,
    val addedAt: Long,
)

@Entity(tableName = "generated_looks")
data class GeneratedLookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fabricId: Long,
    val dressStyleId: String,
    val personImageUri: String,
    val resultImageUri: String,
    val engine: String,
    val isFavorite: Int,
    val createdAt: Long,
    val variationGroup: String? = null,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val labelKey: String?,
    val label: String?,
    val imageUri: String?,
    val timestamp: Long,
)

@Entity(tableName = "usage")
data class UsageEntity(
    @PrimaryKey val key: String,
    val count: Int,
)
