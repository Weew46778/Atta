package com.arena.mineva.assistant

import com.arena.mineva.AppPrefs
import com.arena.mineva.knowledge.KnowledgeRepository
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
                system = "تو «آوا» هستی، دستیار هوشمند و متخصص ماینکرافت فارسی. کوتاه، دقیق و مرحله‌به‌مرحله جواب بده.",
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

    fun localAnswer(rawQuestion: String): String? = knowledge.search(rawQuestion)
}
