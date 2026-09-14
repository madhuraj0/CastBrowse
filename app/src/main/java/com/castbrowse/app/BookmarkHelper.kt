package com.castbrowse.app

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

data class BookmarkItem(
    val url: String,
    val title: String,
    val timestamp: Long
)

object BookmarkHelper {
    fun isUrlBookmarked(context: Context, url: String): Boolean {
        val prefs = EncryptedStorage.getPreferences(context)
        val list = loadBookmarks(prefs)
        return list.any { it.url == url }
    }

    fun toggleBookmark(context: Context, url: String, title: String): Boolean {
        val prefs = EncryptedStorage.getPreferences(context)
        val raw = prefs.getString("bookmarks_json", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        var isNowBookmarked = false
        val newArr = JSONArray()
        var found = false

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("url") == url) {
                found = true
            } else {
                newArr.put(obj)
            }
        }

        if (found) {
            isNowBookmarked = false
            Toast.makeText(context, "Bookmark removed", Toast.LENGTH_SHORT).show()
        } else {
            val entry = JSONObject().apply {
                put("url", url)
                put("title", title.ifEmpty { url })
                put("ts", System.currentTimeMillis())
            }
            newArr.put(entry)
            for (i in 0 until arr.length()) newArr.put(arr.getJSONObject(i))
            isNowBookmarked = true
            Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putString("bookmarks_json", newArr.toString()).apply()
        return isNowBookmarked
    }

    fun loadBookmarks(prefs: SharedPreferences): List<BookmarkItem> {
        val raw = prefs.getString("bookmarks_json", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val result = mutableListOf<BookmarkItem>()
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                result.add(
                    BookmarkItem(
                        url = obj.getString("url"),
                        title = obj.optString("title", ""),
                        timestamp = obj.optLong("ts", 0L)
                    )
                )
            } catch (e: Exception) {}
        }
        return result
    }

    fun deleteBookmark(context: Context, url: String) {
        val prefs = EncryptedStorage.getPreferences(context)
        val raw = prefs.getString("bookmarks_json", "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
        val newArr = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("url") != url) {
                newArr.put(obj)
            }
        }
        prefs.edit().putString("bookmarks_json", newArr.toString()).apply()
        Toast.makeText(context, "Bookmark deleted", Toast.LENGTH_SHORT).show()
    }
}
