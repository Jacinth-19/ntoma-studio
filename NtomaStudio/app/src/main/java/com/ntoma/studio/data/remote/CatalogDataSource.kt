package com.ntoma.studio.data.remote

import android.content.Context
import com.ntoma.studio.data.local.db.DressStyleEntity
import org.json.JSONObject

/**
 * The catalogue source of truth. Today it reads a bundled JSON asset; the same interface can be
 * implemented by an HTTP client that talks to the production backend, and nothing else changes.
 */
interface CatalogDataSource {
    suspend fun fetchStyles(): List<DressStyleEntity>
    suspend fun catalogueVersion(): Int
    suspend fun fetchInspirationLooks(): List<com.ntoma.studio.domain.model.InspirationLook> = emptyList()
}

class AssetCatalogDataSource(private val context: Context) : CatalogDataSource {

    override suspend fun fetchInspirationLooks(): List<com.ntoma.studio.domain.model.InspirationLook> {
        val json = context.assets.open("catalog/looks.json").bufferedReader().use { it.readText() }
        val array = JSONObject(json).getJSONArray("looks")
        return (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            try {
                com.ntoma.studio.domain.model.InspirationLook(
                    id = o.getString("id"),
                    titleKey = o.getString("title_key"),
                    gender = com.ntoma.studio.domain.model.GenderCategory.valueOf(o.getString("gender")),
                    fabrics = o.getJSONArray("fabrics").let { a ->
                        (0 until a.length()).map { com.ntoma.studio.domain.model.FabricCategory.valueOf(a.getString(it)) }
                    },
                    asset = o.getString("asset"),
                )
            } catch (_: IllegalArgumentException) {
                null // Unknown tag in a newer catalog version — skip rather than crash.
            }
        }
    }


    override suspend fun fetchStyles(): List<DressStyleEntity> {
        val json = context.assets.open("catalog/dress_styles.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val array = root.getJSONArray("styles")
        return (0 until array.length()).map { i ->
            val s = array.getJSONObject(i)
            DressStyleEntity(
                id = s.getString("id"),
                titleKey = s.getString("title_key"),
                descriptionKey = s.getString("description_key"),
                gender = s.getString("gender"),
                category = s.getString("category"),
                occasions = s.getJSONArray("occasions").let { a -> (0 until a.length()).joinToString(",") { a.getString(it) } },
                silhouette = s.getString("silhouette"),
                affinity = s.getJSONArray("affinity").let { a -> (0 until a.length()).joinToString(",") { a.getString(it) } },
                structure = s.getDouble("structure").toFloat(),
            )
        }
    }

    override suspend fun catalogueVersion(): Int {
        val json = context.assets.open("catalog/dress_styles.json").bufferedReader().use { it.readText() }
        return JSONObject(json).optInt("version", 1)
    }
}
