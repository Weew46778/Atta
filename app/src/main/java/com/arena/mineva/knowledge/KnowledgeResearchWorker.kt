package com.arena.mineva.knowledge

import android.content.Context
import com.arena.mineva.AppPrefs
import com.arena.mineva.assistant.GeminiAssistantEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A background-minded self-improvement worker.
 *
 * It uses a curated shortlist of high-demand Minecraft topics and asks the configured
 * online model to produce a clear, structured Persian answer. Each answer is saved into
 * the same personal encyclopedia used by [KnowledgeRepository], so later searches (even
 * offline) can find it.
 *
 * The prompt is intentionally generic so the same worker can be reused later for other
 * encyclopedias.
 */
object KnowledgeResearchWorker {

    private val defaultTopics = listOf(
        "ساخت مزرعه آهن خودکار در نسخه های جدید",
        "ساخت مزرعه گلدن اپل یا کلیک فارم",
        "نحوه ساخت پورتال به ندر برای بدراک",
        "چطور سرور Paper را با پلاگین Geyser برای بدراک راه اندازی کنم",
        "مقابله با گریف و محافظت پایگاه با WorldGuard",
        "بهترین روش پیدا کردن الماس و دره الماس در 1.21",
        "ساخت ماشین برداشت و چیدن گندم با ردستون",
        "نحوه دستور دادن به موجودات و ربات در ماین کرافت",
        "انچنت های ضروری شمشیر و زره",
        "ساخت فارم تجربه همراه با اسپاونر",
        "چطور یک سرور بدراک محلی روی گوشی اجرا کنم",
        "تفاوت جاوا و بدراک در ردستون",
        "نحوه استفاده از دستور give برای وسایل خاص",
        "ساخت مزرعه زنبور و عسل",
        "بهترین شیدر و روش نصب برای موبایل"
    )

    data class ResearchResult(
        val researched: Int = 0,
        val saved: Int = 0,
        val skipped: Int = 0,
        val output: String = ""
    )

    suspend fun run(
        context: Context,
        limit: Int = 5,
        topics: List<String> = defaultTopics,
        onProgress: (String) -> Unit = {}
    ): ResearchResult = withContext(Dispatchers.IO) {
        val key = AppPrefs.geminiApiKey
        if (key.isBlank()) {
            onProgress("کلید Gemini موجود نیست؛ تحقیق آنلاین انجام نشد.")
            return@withContext ResearchResult(0, 0, 0, "بدون کلید")
        }

        val repo = KnowledgeRepository(context)
        val selected = topics.take(limit)
        var researched = 0
        var saved = 0
        var skipped = 0

        selected.forEach { topic ->
            onProgress("بررسی: $topic")
            val already = repo.search(topic)?.let { true } ?: false
            if (already) {
                skipped++
                return@forEach
            }
            val answer = runCatching {
                GeminiAssistantEngine.ask(
                    apiKey = key,
                    system = "تو «آوا» هستی. برای موضوع ماینکرافت فارسی پاسخ کامل، مرحله‌به‌مرحله و کاربردی بده. از عنوان شروع کن و با مراحل شماره‌دار ادامه بده.",
                    question = topic,
                    timeoutMs = 40_000
                )
            }.getOrNull()
            researched++
            if (!answer.isNullOrBlank() && answer.length > 40) {
                repo.addLearned(topic, answer, "تحقیق پویا")
                saved++
                onProgress("ذخیره شد: $topic")
            } else {
                skipped++
                onProgress("پاسخ ناقص بود: $topic")
            }
        }

        ResearchResult(
            researched = researched,
            saved = saved,
            skipped = skipped,
            output = "تحقیق: ${selected.size} موضوع | ${saved} ذخیره شد | ${skipped} بدون تغییر"
        )
    }
}
