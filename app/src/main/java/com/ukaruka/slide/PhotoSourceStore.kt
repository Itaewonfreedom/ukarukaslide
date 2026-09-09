package com.ukaruka.slide

import android.content.Context
import android.net.Uri
import org.json.JSONArray

class PhotoSourceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class SourceType { NONE, LOCAL_ALBUM, PICKED_MEDIA }

    val sourceType: SourceType
        get() = runCatching {
            SourceType.valueOf(prefs.getString(KEY_SOURCE_TYPE, SourceType.NONE.name)!!)
        }.getOrDefault(SourceType.NONE)

    val albumIds: List<String>
        get() = readStringList(KEY_ALBUM_IDS).ifEmpty {
            listOfNotNull(prefs.getString(KEY_ALBUM_ID, null))
        }

    val albumNames: List<String>
        get() = readStringList(KEY_ALBUM_NAMES).ifEmpty {
            listOfNotNull(prefs.getString(KEY_ALBUM_NAME, null))
        }

    val slideIntervalMs: Long
        get() = prefs.getLong(KEY_INTERVAL_MS, DEFAULT_INTERVAL_MS)

    fun saveLocalAlbums(albums: List<DeviceAlbum>) {
        prefs.edit()
            .putString(KEY_SOURCE_TYPE, SourceType.LOCAL_ALBUM.name)
            .putString(KEY_ALBUM_IDS, toJson(albums.map { it.id }))
            .putString(KEY_ALBUM_NAMES, toJson(albums.map { it.name }))
            .remove(KEY_ALBUM_ID)
            .remove(KEY_ALBUM_NAME)
            .apply()
    }

    fun savePickedMedia(uris: List<Uri>) {
        val json = JSONArray()
        uris.distinct().forEach { json.put(it.toString()) }
        prefs.edit()
            .putString(KEY_SOURCE_TYPE, SourceType.PICKED_MEDIA.name)
            .putString(KEY_PICKED_URIS, json.toString())
            .remove(KEY_ALBUM_ID)
            .remove(KEY_ALBUM_NAME)
            .apply()
    }

    fun pickedMedia(): List<Uri> {
        return readStringList(KEY_PICKED_URIS).map(Uri::parse)
    }

    fun setSlideIntervalMs(value: Long) {
        prefs.edit().putLong(KEY_INTERVAL_MS, value.coerceIn(5_000L, 60_000L)).apply()
    }

    fun summary(): String = when (sourceType) {
        SourceType.NONE -> "사진 소스가 아직 없습니다."
        SourceType.LOCAL_ALBUM -> "기기 앨범 · ${albumNames.size}개"
        SourceType.PICKED_MEDIA -> "선택한 사진 · ${pickedMedia().size}장 (Google Photos 포함)"
    }

    private fun readStringList(key: String): List<String> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) add(json.getString(index))
            }
        }.getOrDefault(emptyList())
    }

    private fun toJson(values: List<String>): String = JSONArray().apply {
        values.distinct().forEach(::put)
    }.toString()

    companion object {
        private const val PREFS_NAME = "ukaruka_slide"
        private const val KEY_SOURCE_TYPE = "source_type"
        private const val KEY_ALBUM_ID = "album_id"
        private const val KEY_ALBUM_NAME = "album_name"
        private const val KEY_ALBUM_IDS = "album_ids"
        private const val KEY_ALBUM_NAMES = "album_names"
        private const val KEY_PICKED_URIS = "picked_uris"
        private const val KEY_INTERVAL_MS = "interval_ms"
        const val DEFAULT_INTERVAL_MS = 15_000L
    }
}
