package com.ntoma.studio.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FabricEntity::class,
        DressStyleEntity::class,
        FavoriteStyleEntity::class,
        GeneratedLookEntity::class,
        HistoryEntity::class,
        UsageEntity::class,
        CollectionEntity::class,
        CollectionItemEntity::class,
        WardrobeItemEntity::class,
        OutfitEntity::class,
        MeasurementEntity::class,
        StyleFeedbackEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class NtomaDatabase : RoomDatabase() {
    abstract fun fabricDao(): FabricDao
    abstract fun dressStyleDao(): DressStyleDao
    abstract fun lookDao(): GeneratedLookDao
    abstract fun historyDao(): HistoryDao
    abstract fun usageDao(): UsageDao
    abstract fun collectionDao(): CollectionDao
    abstract fun wardrobeDao(): WardrobeDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun styleFeedbackDao(): StyleFeedbackDao
}
