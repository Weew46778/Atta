package com.arena.mineva.assistant

import com.arena.mineva.AppPrefs
import com.arena.mineva.knowledge.KnowledgeRepository
import com.arena.mineva.knowledge.UserTopicStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The assistant brain.
 *
 * Order of resolution:
 *  1. Local Minecraft knowledge base (fast, offline).
 *  2. Configured Gemini backend (needs internet + API key).
 *
 * This is intentionally a small interface so later builds can plug in an on-device LLM
 * (e.g. Gemma/Gemini Nano) or a self-hosted model without changing the UI.
 */
class AssistantEngine(
    private val knowledge: KnowledgeRepository
) {

    suspend fun respond(rawQuestion: String): String = withContext(Dispatchers.IO) {
        val question = rawQuestion.trim()
        if (question.isBlank()) return@withContext "چی بگم؟"

        knowledge.search(question)?.let { return@withContext it }

        val key = AppPrefs.geminiApiKey
        if (key.isBlank()) {
            return@withContext "پاسخش هنوز در حافظه محلی من نیست و برای جستجوی آنلاین باید کلید هوش مصنوعی را در تنظیمات بگذاری یا اینترنت متصل باشد."
        }

        runCatching {
            val answer = GeminiAssistantEngine.ask(
                apiKey = key,
                system = "تو «آوا» هستی، دستیار هوشمند فارسی. متخصص ماینکرافت و همچنین راهنمای دانش عمومی هستی؛ اگر سؤال خارج از ماینکرافت هم بود، کوتاه، دقیق و مرحله‌به‌مرحله جواب بده.",
                question = question
            )
            // Learn: the assistant writes the answer into its personal encyclopedia so it
            // becomes available offline the next time a similar question is asked.
            if (answer.isNotBlank() && answer.length > 20) {
                knowledge.addLearned(question, answer)
            }
            answer
        }.getOrElse { e ->
            "خطا در اتصال به هوش مصنوعی: ${e.message ?: "نامشخص"}. اینترنت و کلید را بررسی کن."
        }
    }

    /**
     * Handles "یاد بگیر / به خاطر بسپار / اضافه کن به دانش" requests from chat.
     * The assistant extracts the topic, optionally researches it online, and stores it
     * both as a user topic and in the personal encyclopedia for future offline answers.
     */
    suspend fun learn(rawQuestion: String): String = withContext(Dispatchers.IO) {
        val q = rawQuestion.trim()
        val topic = extractLearnTopic(q)
        if (topic.isBlank()) {
            return@withContext "متوجه نشدم چه چیزی را یاد بگیرم. بگو: «یاد بگیر: نام موضوع»."
        }

        val key = AppPrefs.geminiApiKey
        val answer = if (key.isBlank()) {
            "موضوع «$topic» را ثبت کردم. برای توضیح کامل‌تر در آینده، کلید هوش مصنوعی را اضافه کن."
        } else {
            runCatching {
                GeminiAssistantEngine.ask(
                    apiKey = key,
                    system = "تو «آوا» هستی. برای این موضوع به فارسی پاسخ کامل، دقیق و مرحله‌به‌مرحله بده.",
                    question = topic,
                    timeoutMs = 35_000
                )
            }.getOrElse { e -> "نتوانستم آنلاین اطلاعات پیدا کنم: ${e.message ?: "نامشخص"}" }
        }

        if (answer.isNotBlank() && answer.length > 20) {
            knowledge.addLearned(topic, answer, "یادگیری توسط کاربر")
        }
        UserTopicStore.add(topic, "دایرةالمعارف کاربر", answer)

        "یاد گرفتم: «$topic». دفعه بعد همین را بپرسی، همین‌جا جواب دارم."
    }

    private fun extractLearnTopic(q: String): String {
        val prefixes = listOf(
            "یاد بگیر", "یاد بگیر:", "یاد بده", "این را یاد بگیر",
            "به خاطر بسپار", "اضافه کن به دانش", "اضافه کن به دایرةالمعارف",
            "یادم بده", "ذخیره کن"
        )
        var topic = q
        for (prefix in prefixes) {
            if (topic.startsWith(prefix, ignoreCase = true)) {
                topic = topic.removePrefix(prefix)
                topic = topic.removePrefix(":").removePrefix(":").trim()
            }
        }
        // Also strip common lead phrases.
        topic = topic.trim().removePrefix("بگو").removePrefix("خواهش").trim()
        return topic
    }

    fun localAnswer(rawQuestion: String): String? = knowledge.search(rawQuestion)
}
