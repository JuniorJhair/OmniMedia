package com.example.player.controller

import android.content.Context
import android.content.SharedPreferences
import com.example.core.model.MediaType
import com.example.player.model.PlaylistItem
import com.example.player.model.RepeatMode
import com.example.player.model.resolveEffectiveVideoSpeed
import org.json.JSONArray
import org.json.JSONObject

data class PersistedQueueState(
    val items: List<PlaylistItem>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
)

class QueuePersistenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveQueue(
        items: List<PlaylistItem>,
        currentIndex: Int,
        positionMs: Long,
        shuffleEnabled: Boolean = false,
        repeatMode: RepeatMode = RepeatMode.OFF
    ) {
        if (items.isEmpty()) {
            clearQueue()
            return
        }

        try {
            val jsonArray = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("uri", item.uri)
                    put("title", item.title)
                    put("artist", item.artist)
                    put("album", item.album)
                    put("artworkUri", item.artworkUri ?: "")
                    put("durationMs", item.durationMs)
                    put("mediaType", item.mediaType.name)
                }
                jsonArray.put(obj)
            }

            prefs.edit()
                .putString(KEY_ITEMS_JSON, jsonArray.toString())
                .putInt(KEY_CURRENT_INDEX, currentIndex.coerceIn(0, items.size - 1))
                .putLong(KEY_POSITION_MS, positionMs.coerceAtLeast(0L))
                .putBoolean(KEY_SHUFFLE, shuffleEnabled)
                .putString(KEY_REPEAT, repeatMode.name)
                .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
                .commit()
        } catch (_: Exception) {}
    }

    fun updatePosition(positionMs: Long, currentIndex: Int? = null) {
        val editor = prefs.edit().putLong(KEY_POSITION_MS, positionMs.coerceAtLeast(0L))
        if (currentIndex != null) {
            editor.putInt(KEY_CURRENT_INDEX, currentIndex)
        }
        editor.commit()
    }

    fun loadSavedQueue(): PersistedQueueState? {
        val jsonStr = prefs.getString(KEY_ITEMS_JSON, null) ?: return null
        if (jsonStr.isEmpty()) return null

        return try {
            val jsonArray = JSONArray(jsonStr)
            val items = mutableListOf<PlaylistItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val uri = obj.optString("uri", "")
                if (uri.isEmpty()) continue

                val title = obj.optString("title", "Pista")
                val artist = obj.optString("artist", "")
                val album = obj.optString("album", "")
                val artworkUri = obj.optString("artworkUri", "").ifEmpty { null }
                val durationMs = obj.optLong("durationMs", 0L)
                val mediaTypeName = obj.optString("mediaType", MediaType.AUDIO.name)
                val mediaType = runCatching { MediaType.valueOf(mediaTypeName) }.getOrDefault(MediaType.AUDIO)

                items.add(
                    PlaylistItem(
                        uri = uri,
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUri = artworkUri,
                        durationMs = durationMs,
                        mediaType = mediaType
                    )
                )
            }

            if (items.isEmpty()) return null

            val savedIndex = prefs.getInt(KEY_CURRENT_INDEX, 0).coerceIn(0, items.size - 1)
            val savedPos = prefs.getLong(KEY_POSITION_MS, 0L)
            val shuffle = prefs.getBoolean(KEY_SHUFFLE, false)
            val repeatStr = prefs.getString(KEY_REPEAT, RepeatMode.OFF.name) ?: RepeatMode.OFF.name
            val repeat = runCatching { RepeatMode.valueOf(repeatStr) }.getOrDefault(RepeatMode.OFF)

            PersistedQueueState(
                items = items,
                currentIndex = savedIndex,
                positionMs = savedPos,
                shuffleEnabled = shuffle,
                repeatMode = repeat
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearQueue() {
        prefs.edit()
            .remove(KEY_ITEMS_JSON)
            .remove(KEY_CURRENT_INDEX)
            .remove(KEY_POSITION_MS)
            .remove(KEY_SHUFFLE)
            .remove(KEY_REPEAT)
            .remove(KEY_TIMESTAMP)
            .commit()
    }

    fun saveGlobalVideoSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_GLOBAL_VIDEO_SPEED, speed).commit()
    }

    fun getGlobalVideoSpeed(): Float {
        return prefs.getFloat(KEY_GLOBAL_VIDEO_SPEED, 1.0f)
    }

    fun saveVideoSpecificSpeed(uri: String, speed: Float) {
        if (uri.isEmpty()) return
        try {
            val mapJson = prefs.getString(KEY_VIDEO_SPEED_MAP, "{}") ?: "{}"
            val obj = JSONObject(mapJson)
            obj.put(uri, speed.toDouble())
            prefs.edit().putString(KEY_VIDEO_SPEED_MAP, obj.toString()).commit()
        } catch (_: Exception) {}
    }

    fun getVideoSpecificSpeed(uri: String): Float? {
        if (uri.isEmpty()) return null
        return try {
            val mapJson = prefs.getString(KEY_VIDEO_SPEED_MAP, null) ?: return null
            val obj = JSONObject(mapJson)
            if (obj.has(uri)) obj.getDouble(uri).toFloat() else null
        } catch (_: Exception) {
            null
        }
    }

    fun clearVideoSpecificSpeed(uri: String) {
        if (uri.isEmpty()) return
        try {
            val mapJson = prefs.getString(KEY_VIDEO_SPEED_MAP, null) ?: return
            val obj = JSONObject(mapJson)
            if (obj.has(uri)) {
                obj.remove(uri)
                prefs.edit().putString(KEY_VIDEO_SPEED_MAP, obj.toString()).commit()
            }
        } catch (_: Exception) {}
    }

    fun clearAllVideoSpeeds() {
        prefs.edit()
            .remove(KEY_GLOBAL_VIDEO_SPEED)
            .remove(KEY_VIDEO_SPEED_MAP)
            .commit()
    }

    fun resolveVideoSpeed(uri: String): Float {
        return resolveEffectiveVideoSpeed(
            specificVideoSpeed = getVideoSpecificSpeed(uri),
            globalVideoSpeed = getGlobalVideoSpeed()
        )
    }

    companion object {
        private const val PREFS_NAME = "omni_queue_prefs"
        private const val KEY_ITEMS_JSON = "queue_items_json"
        private const val KEY_CURRENT_INDEX = "queue_current_index"
        private const val KEY_POSITION_MS = "queue_position_ms"
        private const val KEY_SHUFFLE = "queue_shuffle"
        private const val KEY_REPEAT = "queue_repeat"
        private const val KEY_TIMESTAMP = "queue_saved_timestamp"
        private const val KEY_GLOBAL_VIDEO_SPEED = "global_video_speed"
        private const val KEY_VIDEO_SPEED_MAP = "video_specific_speed_map_json"
    }
}
