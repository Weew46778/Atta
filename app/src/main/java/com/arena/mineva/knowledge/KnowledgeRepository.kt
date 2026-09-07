package com.arena.mineva.knowledge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class KnowledgeEntry(
    val id: String,
    val title: String,
    val category: String,
    val text: String,
    val tags: List<String>,
    val steps: List<String>
)

/**
 * Local, searchable Minecraft encyclopedia.
 *
 * The assistant first searches here. When an answer is missing it asks the configured
 * online engine and then writes the result back into [extraEntries] so the personal
 * encyclopedia grows over time.
 */
class KnowledgeRepository(context: Context) {

    private val entries: List<KnowledgeEntry> =
        (runCatching { load(context) }.getOrDefault(emptyList()) +
            runCatching { loadExtraBase(context) }.getOrDefault(emptyList()) +
            loadUserTopics()).distinctBy { it.id }

    private val extraFile: File = File(context.filesDir, "knowledge_extra.json")
    private val extraEntries: MutableList<KnowledgeEntry> = runCatching {
        loadJson(extraFile.readText())
    }.getOrDefault(mutableListOf()).toMutableList()

    private fun all(): List<KnowledgeEntry> = entries + extraEntries

    fun search(query: String): String? {
        val q = query.trim().lowercase()
        if (q.isBlank()) return null
        val scored = all()
            .mapNotNull { entry ->
                val title = entry.title.lowercase()
                val text = entry.text.lowercase()
                val tags = entry.tags.joinToString(" ").lowercase()
                var score = 0
                if (title.contains(q)) score += 50
                if (tags.contains(q)) score += 30
                if (text.contains(q)) score += 10
                val words = q.split(" ").filter { it.length > 2 }
                words.forEach { w ->
                    if (title.contains(w)) score += 25
                    if (tags.contains(w)) score += 15
                    if (text.contains(w)) score += 5
                }
                if (score > 0) score to entry else null
            }
            .sortedByDescending { it.first }
        return scored.firstOrNull()?.let { (_, e) -> format(e) }
    }

    fun topics(category: String? = null): List<KnowledgeEntry> = all().filter {
        category == null || it.category.equals(category, ignoreCase = true)
    }

    fun categories(): List<String> = all().map { it.category }.distinct()

    /**
     * Saves a newly learned answer into the personal local encyclopedia.
     * Returns the created entry.
     */
    fun addLearned(
        question: String,
        answer: String,
        category: String = "یادگیری پویا"
    ): KnowledgeEntry {
        val title = question.trim().let { if (it.length > 60) it.take(57) + "..." else it }
        val entry = KnowledgeEntry(
            id = "learned_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            title = title,
            category = category,
            text = answer.trim(),
            tags = question.split(" ").filter { it.length > 2 }.take(12),
            steps = emptyList()
        )
        extraEntries.removeAll { it.title.equals(entry.title, ignoreCase = true) }
        extraEntries += entry
        saveExtra()
        return entry
    }

    fun learnedCount(): Int = extraEntries.size

    /**
     * Adds a user-defined encyclopedia topic (Minecraft or any other category).
     * This is how the same framework supports up to (and beyond) five additional
     * non-Minecraft topics added by the user.
     */
    fun addUserTopic(title: String, text: String, category: String, tags: List<String> = emptyList()): KnowledgeEntry {
        val entry = KnowledgeEntry(
            id = "user_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
            title = title.trim(),
            category = category.trim().ifBlank { "دایرةالمعارف کاربر" },
            text = text.trim(),
            tags = tags.ifEmpty { title.split(" ").filter { it.length > 2 }.take(10) },
            steps = emptyList()
        )
        extraEntries.removeAll { it.title.equals(entry.title, ignoreCase = true) }
        extraEntries += entry
        saveExtra()
        return entry
    }

    /** Human-readable summary of all categories. */
    fun categorySummary(): String {
        if (all().isEmpty()) return "هیچ موضوعی ثبت نشده است."
        return all()
            .groupBy { it.category }
            .toSortedMap()
            .map { (cat, list) -> "$cat: ${list.size} موضوع" }
            .joinToString(" | ")
    }

    /** All topics in a category, sorted by title. */
    fun topicsIn(category: String): List<KnowledgeEntry> =
        topics(category).sortedBy { it.title }

    private fun saveExtra() {
        val arr = JSONArray()
        extraEntries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("category", e.category)
                    .put("text", e.text)
                    .put("tags", JSONArray(e.tags))
                    .put("steps", JSONArray(e.steps))
            )
        }
        extraFile.parentFile?.mkdirs()
        extraFile.writeText(arr.toString())
    }

    private fun format(e: KnowledgeEntry): String {
        val steps = if (e.steps.isEmpty()) "" else "\n\nمراحل:\n" + e.steps.joinToString("\n") { "• $it" }
        return "${e.title}\n\n${e.text}$steps"
    }

    private fun load(context: Context): List<KnowledgeEntry> {
        val json = context.assets.open("knowledge_base.json")
            .bufferedReader()
            .use { it.readText() }
        return loadJson(json)
    }

    private fun loadExtraBase(context: Context): List<KnowledgeEntry> = runCatching {
        val json = context.assets.open("knowledge_extra_topics.json")
            .bufferedReader()
            .use { it.readText() }
        loadJson(json)
    }.getOrDefault(emptyList())

    private fun loadUserTopics(): List<KnowledgeEntry> =
        com.arena.mineva.knowledge.UserTopicStore.all().map { t ->
            KnowledgeEntry(
                id = "user_topic_${t.title.lowercase().replace(" ", "_")}",
                title = t.title,
                category = t.category,
                text = t.detail.ifBlank { t.title },
                tags = t.title.split(" ").filter { it.length > 2 }.take(10),
                steps = emptyList()
            )
        }

    private fun loadJson(json: String): List<KnowledgeEntry> {
        val arr = JSONArray(json)
        val out = mutableListOf<KnowledgeEntry>()
        for (i in 0 until arr.length()) {
            val o: JSONObject = arr.getJSONObject(i)
            out += KnowledgeEntry(
                id = o.optString("id"),
                title = o.optString("title"),
                category = o.optString("category"),
                text = o.optString("text"),
                tags = o.optJSONArray("tags").toStringList(),
                steps = o.optJSONArray("steps").toStringList()
            )
        }
        return out
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { getString(it) }
    }
}
