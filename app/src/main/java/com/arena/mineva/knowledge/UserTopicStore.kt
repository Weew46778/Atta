package com.arena.mineva.knowledge

import com.arena.mineva.AppPrefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-selected encyclopedia topics. Up to five non-Minecraft topics chosen by the user.
 * The same store can hold more, but the voice-driven first run collects exactly five.
 */
object UserTopicStore {

    data class Topic(val title: String, val category: String, val detail: String = "")

    fun all(): List<Topic> = runCatching {
        val arr = JSONArray(AppPrefs.userTopicsJson)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Topic(o.optString("title"), o.optString("category"), o.optString("detail"))
        }
    }.getOrDefault(emptyList())

    fun add(title: String, category: String, detail: String = ""): Topic {
        val all = all().toMutableList()
        val t = Topic(title.trim(), category.trim().ifBlank { "دایرة‌المعارف کاربر" }, detail.trim())
        all.removeAll { it.title.equals(t.title, ignoreCase = true) }
        all += t
        save(all)
        return t
    }

    fun remove(title: String) {
        save(all().filterNot { it.title.equals(title, ignoreCase = true) })
    }

    fun count(): Int = all().size

    fun remainingCapacity(max: Int = 5): Int = (max - count()).coerceAtLeast(0)

    private fun save(list: List<Topic>) {
        val arr = JSONArray()
        list.take(50).forEach { t ->
            arr.put(JSONObject().apply {
                put("title", t.title)
                put("category", t.category)
                put("detail", t.detail)
            })
        }
        AppPrefs.userTopicsJson = arr.toString()
    }
}
