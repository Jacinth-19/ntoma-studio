package com.ntoma.studio.di

import android.content.Context
import androidx.room.Room
import com.ntoma.studio.ads.AdProvider
import com.ntoma.studio.ads.DemoAdProvider
import com.ntoma.studio.analytics.AnalyticsLogger
import com.ntoma.studio.analytics.CrashReporter
import com.ntoma.studio.billing.BillingManager
import com.ntoma.studio.billing.DemoBillingManager
import com.ntoma.studio.analytics.DebugAnalyticsLogger
import com.ntoma.studio.data.local.db.NtomaDatabase
import com.ntoma.studio.data.local.prefs.SettingsRepositoryImpl
import com.ntoma.studio.data.local.prefs.TooltipStore
import com.ntoma.studio.data.remote.AssetCatalogDataSource
import com.ntoma.studio.data.repository.DemoLookGenerator
import com.ntoma.studio.data.repository.DressStyleRepositoryImpl
import com.ntoma.studio.data.repository.EntitlementRepositoryImpl
import com.ntoma.studio.data.repository.FabricAnalysisRepositoryImpl
import com.ntoma.studio.data.repository.FavoritesRepositoryImpl
import com.ntoma.studio.data.repository.HistoryRepositoryImpl
import com.ntoma.studio.data.repository.VirtualTryOnRepositoryImpl
import com.ntoma.studio.domain.repository.FabricAnalysisRepository
import com.ntoma.studio.domain.repository.FavoritesRepository
import com.ntoma.studio.domain.repository.HistoryRepository
import com.ntoma.studio.domain.repository.SettingsRepository
import com.ntoma.studio.domain.repository.VirtualTryOnRepository
import com.ntoma.studio.media.BitmapAnalyzer
import com.ntoma.studio.media.ImageProcessor
import com.ntoma.studio.notifications.NtomaNotifier

/**
 * Manual dependency container: explicit, testable, zero magic. Tests build the same repositories
 * with fakes.
 */
class AppContainer private constructor(context: Context) {

    private val appContext = context.applicationContext

    val database: NtomaDatabase by lazy {
        Room.databaseBuilder(appContext, NtomaDatabase::class.java, "ntoma.db")
            // Pre-1.0 schema is allowed to evolve destructively; after release ship migrations.
            .fallbackToDestructiveMigration()
            .build()
    }

    val analytics: AnalyticsLogger = DebugAnalyticsLogger()
    val crashReporter = CrashReporter(appContext, analytics)
    val adProvider: AdProvider = DemoAdProvider()
    val notifier = NtomaNotifier(appContext)

    val imageProcessor = ImageProcessor(appContext)
    val bitmapAnalyzer = BitmapAnalyzer(appContext)

    val settings: SettingsRepository = SettingsRepositoryImpl(appContext)
    val billing: BillingManager = DemoBillingManager(settings)
    val tooltips = TooltipStore(appContext)

    val history: HistoryRepository = HistoryRepositoryImpl(database.historyDao(), settings)

    val catalog = AssetCatalogDataSource(appContext)
    val dressStyles = DressStyleRepositoryImpl(database.dressStyleDao(), catalog)

    val mlClassifier = com.ntoma.studio.data.ml.OnDeviceMlClassifier(appContext)

    val cloudAnalysis = com.ntoma.studio.data.remote.CloudAnalysisClient()
    val personSegmenter = com.ntoma.studio.data.ml.PersonSegmenter(appContext)

    val fabricAnalysis: FabricAnalysisRepository = FabricAnalysisRepositoryImpl(
        bitmapAnalyzer, database.fabricDao(), history, analytics, mlClassifier, settings, cloudAnalysis,
    )

    val favorites: FavoritesRepository = FavoritesRepositoryImpl(
        database.fabricDao(), database.dressStyleDao(), database.lookDao(),
    )

    val entitlements = EntitlementRepositoryImpl(database.usageDao(), settings)

    val collections: com.ntoma.studio.domain.repository.CollectionsRepository =
        com.ntoma.studio.data.repository.CollectionsRepositoryImpl(database.collectionDao())
    val wardrobe: com.ntoma.studio.domain.repository.WardrobeRepository =
        com.ntoma.studio.data.repository.WardrobeRepositoryImpl(database.wardrobeDao())
    val measurements: com.ntoma.studio.domain.repository.MeasurementsRepository =
        com.ntoma.studio.data.repository.MeasurementsRepositoryImpl(database.measurementDao())
    val feedback: com.ntoma.studio.domain.repository.FeedbackRepository =
        com.ntoma.studio.data.repository.FeedbackRepositoryImpl(database.styleFeedbackDao())
    val tailors: com.ntoma.studio.domain.repository.TailorRepository =
        com.ntoma.studio.data.remote.DemoTailorSource()

    val lookGenerator = DemoLookGenerator(imageProcessor)

    val tryOn: VirtualTryOnRepository = VirtualTryOnRepositoryImpl(
        database.lookDao(),
        database.fabricDao(),
        database.dressStyleDao(),
        lookGenerator,
        history,
        notifier,
        analytics,
        settings,
    )

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }

        /** Test hook. */
        fun reset() {
            instance = null
        }
    }
}
