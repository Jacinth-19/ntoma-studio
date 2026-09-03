package com.ntoma.studio.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ntoma.studio.data.local.db.NtomaDatabase
import com.ntoma.studio.domain.model.CollectionItemType
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
class CollectionsRepositoryTest {

    private lateinit var db: NtomaDatabase
    private lateinit var repo: CollectionsRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, NtomaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = CollectionsRepositoryImpl(db.collectionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create then observe returns the collection`() = runBlocking {
        val id = repo.create("Wedding Ideas")
        val list = repo.observeCollections().first()
        assertEquals(1, list.size)
        assertEquals(id, list.first().id)
        assertEquals("Wedding Ideas", list.first().name)
    }

    @Test
    fun `items can be added, observed and removed`() = runBlocking {
        val id = repo.create("My Kente")
        repo.addItem(id, CollectionItemType.FABRIC, "7")
        repo.addItem(id, CollectionItemType.DESIGN, "kaba_slit")

        val collection = repo.observeCollection(id).first()
        assertEquals(2, collection?.items?.size)
        assertTrue(repo.isInAnyCollection(CollectionItemType.FABRIC, "7"))

        repo.removeItem(id, CollectionItemType.FABRIC, "7")
        assertFalse(repo.isInAnyCollection(CollectionItemType.FABRIC, "7"))
        assertEquals(1, repo.observeCollection(id).first()?.items?.size)
    }

    @Test
    fun `deleting a collection deletes its items`() = runBlocking {
        val id = repo.create("Christmas")
        repo.addItem(id, CollectionItemType.LOOK, "3")
        repo.delete(id)
        assertTrue(repo.observeCollections().first().isEmpty())
        assertFalse(repo.isInAnyCollection(CollectionItemType.LOOK, "3"))
    }
}
