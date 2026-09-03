package com.ntoma.studio.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ntoma.studio.data.local.db.NtomaDatabase
import com.ntoma.studio.data.local.prefs.SettingsRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EntitlementRepositoryTest {

    private lateinit var db: NtomaDatabase
    private lateinit var settings: SettingsRepositoryImpl
    private lateinit var repo: EntitlementRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, NtomaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepositoryImpl(context)
        // DataStore state can leak between Robolectric tests; pin the free tier explicitly.
        runBlocking { settings.update { it.copy(premiumEnabled = false) } }
        repo = EntitlementRepositoryImpl(db.usageDao(), settings)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `free tier allows three analyses per day then blocks`() = runBlocking {
        repeat(3) {
            assertTrue(repo.canAnalyze())
            repo.consumeAnalysis()
        }
        assertFalse(repo.canAnalyze())
        assertEquals(0, repo.remainingAnalysesToday().first())
    }

    @Test
    fun `free tier allows two try-ons per month and bonuses add one each`() = runBlocking {
        repeat(2) {
            assertTrue(repo.canTryOn())
            repo.consumeTryOn()
        }
        assertFalse(repo.canTryOn())
        repo.grantBonusTryOn()
        assertTrue(repo.canTryOn())
        repo.consumeTryOn()
        assertFalse(repo.canTryOn())
    }

    @Test
    fun `premium removes quota limits`() = runBlocking {
        settings.update { it.copy(premiumEnabled = true) }
        repeat(10) {
            assertTrue(repo.canAnalyze())
            repo.consumeAnalysis()
            assertTrue(repo.canTryOn())
            repo.consumeTryOn()
        }
        assertEquals(EntitlementRepositoryImpl.UNLIMITED_SENTINEL, repo.remainingTryOnsThisMonth().first())
    }

    @Test
    fun `remaining try-ons reflects monthly usage`() = runBlocking {
        assertEquals(2, repo.remainingTryOnsThisMonth().first())
        repo.consumeTryOn()
        assertEquals(1, repo.remainingTryOnsThisMonth().first())
    }
}
