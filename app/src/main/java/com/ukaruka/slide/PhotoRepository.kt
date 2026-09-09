package com.ukaruka.slide

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class DeviceAlbum(val id: String, val name: String, val photoCount: Int)
data class SlidePhoto(val uri: Uri, val capturedAtMs: Long)

class PhotoRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun loadConfiguredPhotos(store: PhotoSourceStore): List<SlidePhoto> = when (store.sourceType) {
        PhotoSourceStore.SourceType.NONE -> emptyList()
        PhotoSourceStore.SourceType.LOCAL_ALBUM -> runCatching {
            loadAlbumPhotos(store.albumIds)
        }.getOrDefault(emptyList())
    }

    fun loadAlbums(): List<DeviceAlbum> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val albums = linkedMapOf<String, Pair<String, Int>>()

        resolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media.SIZE} > 0",
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idColumn) ?: continue
                val name = cursor.getString(nameColumn) ?: "이름 없는 앨범"
                val current = albums[id]
                albums[id] = name to ((current?.second ?: 0) + 1)
            }
        }

        return albums.map { (id, value) -> DeviceAlbum(id, value.first, value.second) }
            .sortedWith(compareByDescending<DeviceAlbum> { it.photoCount }.thenBy { it.name })
    }

    private fun loadAlbumPhotos(albumIds: List<String>): List<SlidePhoto> {
        if (albumIds.isEmpty()) return emptyList()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )
        val photos = mutableListOf<SlidePhoto>()
        val placeholders = albumIds.joinToString(",") { "?" }

        resolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders) AND ${MediaStore.Images.Media.SIZE} > 0",
            albumIds.toTypedArray(),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val takenAt = cursor.getLong(takenColumn).takeIf { it > 0L }
                    ?: cursor.getLong(addedColumn).times(1_000L)
                photos += SlidePhoto(
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn)),
                    capturedAtMs = takenAt
                )
            }
        }
        return photos
    }

}
