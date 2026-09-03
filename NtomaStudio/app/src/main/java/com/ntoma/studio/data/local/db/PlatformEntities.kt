package com.ntoma.studio.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "collection_items",
    indices = [Index("collectionId")],
)
data class CollectionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionId: Long,
    /** FABRIC | DESIGN | LOOK */
    val itemType: String,
    /** Row id (fabrics/looks) or style id (designs), stored as string for uniformity. */
    val itemId: String,
    val addedAt: Long,
)

@Entity(tableName = "wardrobe_items")
data class WardrobeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** TOP | BOTTOM | DRESS | SHOES | BAG | ACCESSORY */
    val category: String,
    val imageUri: String,
    val notes: String?,
    val createdAt: Long,
)

@Entity(tableName = "outfits")
data class OutfitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** CSV of wardrobe item ids. */
    val itemIdsCsv: String,
    val createdAt: Long,
)

/** Single-row table (id = 1) with the user's optional measurements in centimetres. */
@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey val id: Int = 1,
    val heightCm: Int?,
    val chestCm: Int?,
    val waistCm: Int?,
    val hipCm: Int?,
    val shoulderCm: Int?,
    val sleeveCm: Int?,
    val inseamCm: Int?,
    val neckCm: Int?,
    val notes: String?,
    val updatedAt: Long,
)

/** "Not interested" dismissals + saved-design feedback, used to personalise recommendations. */
@Entity(tableName = "style_feedback")
data class StyleFeedbackEntity(
    @PrimaryKey val styleId: String,
    val dismissed: Int,
    val updatedAt: Long,
)
