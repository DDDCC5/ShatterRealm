package com.example.shatterrealm.data.repository

import android.content.Context
import com.example.shatterrealm.domain.model.Item
import com.example.shatterrealm.platform.CustomModelImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

class CatalogRepository(private val context: Context) {
    val modelsDirectory = File(context.getExternalFilesDir(null) ?: context.filesDir, "models").apply { mkdirs() }
    fun builtins(): List<Item> {
        val array = JSONArray(context.assets.open("catalog.json").bufferedReader().use { it.readText() })
        return (0 until array.length()).map { n -> array.getJSONObject(n).let {
            Item(it.getString("id"),it.getString("zh"),it.getString("en"),it.getString("material"),
                "asset://models/${it.getString("id")}")
        } }
    }
    data class Scan(val items: List<Item>, val failures: List<String>)
    suspend fun refresh(): Scan = withContext(Dispatchers.IO) {
        val items = mutableListOf<Item>(); val failures = mutableListOf<String>()
        modelsDirectory.listFiles()?.filter { it.isFile && it.extension.lowercase() in setOf("glb","fbx","obj") }
            ?.sortedBy { it.name.lowercase() }?.forEach { source ->
                runCatching { CustomModelImporter.import(context, source) }
                    .onSuccess { items += it }.onFailure { failures += source.name }
            }
        Scan(items,failures)
    }
}
