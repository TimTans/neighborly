package com.example.android.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GroceryListItemRecord(
    val id: String,
    val productId: String?,
    val upc: String,
    val name: String,
    val unitSize: String,
    val store: String,
    val price: Double,
    val quantity: Int,
    val dateAddedMillis: Long
)

interface GroceryListLocalDataSource {
    fun loadItems(): List<GroceryListItemRecord>
    fun saveItems(items: List<GroceryListItemRecord>)
}

class SharedPreferencesGroceryListLocalDataSource(
    context: Context
) : GroceryListLocalDataSource {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun loadItems(): List<GroceryListItemRecord> {
        val rawItems = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(rawItems)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toRecord())
                }
            }
        }.getOrDefault(emptyList())
    }

    override fun saveItems(items: List<GroceryListItemRecord>) {
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun JSONObject.toRecord(): GroceryListItemRecord {
        return GroceryListItemRecord(
            id = getString("id"),
            productId = optString("productId").takeUnless { it.isBlank() },
            upc = getString("upc"),
            name = getString("name"),
            unitSize = getString("unitSize"),
            store = getString("store"),
            price = optDouble("price", 0.0),
            quantity = optInt("quantity", 1).coerceAtLeast(1),
            dateAddedMillis = optLong("dateAddedMillis", System.currentTimeMillis())
        )
    }

    private fun GroceryListItemRecord.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("productId", productId.orEmpty())
            .put("upc", upc)
            .put("name", name)
            .put("unitSize", unitSize)
            .put("store", store)
            .put("price", price)
            .put("quantity", quantity)
            .put("dateAddedMillis", dateAddedMillis)
    }

    private companion object {
        const val PREFERENCES_NAME = "neighborly_grocery_list"
        const val KEY_ITEMS = "items"
    }
}
