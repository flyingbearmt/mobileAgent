package com.example.mobileagent.intent

import android.content.Context
import com.example.mobileagent.agent.SkillInfo
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnDeviceSkillRouter(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val lock = Any()
    private var embedder: TextEmbedder? = null

    data class RouteResult(
        val skillName: String,
        val score: Double,
        val margin: Double,
    )

    private data class CachedEmbedding(
        val routingText: String,
        val embedding: com.google.mediapipe.tasks.components.containers.Embedding,
    )

    private val cache = mutableMapOf<String, CachedEmbedding>()

    private fun getEmbedder(): TextEmbedder? {
        synchronized(lock) {
            val existing = embedder
            if (existing != null) return existing

            return runCatching {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET_PATH)
                    .build()
                val options = TextEmbedder.TextEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .build()
                TextEmbedder.createFromOptions(appContext, options)
            }.onSuccess { created ->
                embedder = created
            }.getOrNull()
        }
    }

    suspend fun routeSkill(
        instruction: String,
        context: Map<String, Any?>,
        skills: List<SkillInfo>,
    ): RouteResult? = withContext(Dispatchers.Default) {
        val localEmbedder = getEmbedder() ?: return@withContext null

        val query = buildQueryText(instruction = instruction, context = context)
        if (query.isBlank()) return@withContext null

        val queryEmbedding = runCatching {
            localEmbedder.embed(query).embeddingResult().embeddings().first()
        }.getOrNull() ?: return@withContext null

        var bestName: String? = null
        var bestScore = Double.NEGATIVE_INFINITY
        var secondScore = Double.NEGATIVE_INFINITY

        for (s in skills) {
            val routingText = s.routingText?.takeIf { it.isNotBlank() } ?: continue

            val trimmedRoutingText = truncateText(routingText, maxChars = ROUTING_TEXT_MAX_CHARS)

            val skillEmbedding = getOrComputeSkillEmbedding(localEmbedder, s.name, trimmedRoutingText) ?: continue
            val score = runCatching {
                TextEmbedder.cosineSimilarity(queryEmbedding, skillEmbedding)
            }.getOrNull() ?: continue

            if (score > bestScore) {
                secondScore = bestScore
                bestScore = score
                bestName = s.name
            } else if (score > secondScore) {
                secondScore = score
            }
        }

        val margin = bestScore - secondScore
        if (bestName == null) return@withContext null
        if (bestScore < MIN_SCORE) return@withContext null
        if (margin < MIN_MARGIN) return@withContext null

        RouteResult(skillName = bestName, score = bestScore, margin = margin)
    }

    private fun getOrComputeSkillEmbedding(
        embedder: TextEmbedder,
        skillName: String,
        routingText: String,
    ): com.google.mediapipe.tasks.components.containers.Embedding? {
        synchronized(lock) {
            val existing = cache[skillName]
            if (existing != null && existing.routingText == routingText) {
                return existing.embedding
            }
        }

        val computed = runCatching {
            embedder.embed(routingText).embeddingResult().embeddings().first()
        }.getOrNull() ?: return null

        synchronized(lock) {
            cache[skillName] = CachedEmbedding(routingText = routingText, embedding = computed)
        }
        return computed
    }

    private fun buildQueryText(instruction: String, context: Map<String, Any?>): String {
        val sharedTextRaw = context["sharedText"] as? String
        val sourceApp = context["sourceApp"] as? String
        val locale = context["locale"] as? String
        val network = (context["deviceState"] as? Map<*, *>)?.get("network") as? String

        val sharedText = sharedTextRaw?.let { truncateText(it, maxChars = SHARED_TEXT_MAX_CHARS) }

        val parts = ArrayList<String>()
        if (instruction.isNotBlank()) parts.add("Instruction: $instruction")
        if (!sharedText.isNullOrBlank()) parts.add("SharedText: $sharedText")
        if (!sourceApp.isNullOrBlank()) parts.add("SourceApp: $sourceApp")
        if (!locale.isNullOrBlank()) parts.add("Locale: $locale")
        if (!network.isNullOrBlank()) parts.add("Network: $network")
        return parts.joinToString("\n")
    }

    private fun truncateText(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val head = (maxChars * 3) / 4
        val tail = maxChars - head
        return text.take(head) + "\n...\n" + text.takeLast(tail)
    }

    companion object {
        const val MODEL_ASSET_PATH = "universal_sentence_encoder_qa_ondevice.tflite"
        private const val SHARED_TEXT_MAX_CHARS = 1800
        private const val ROUTING_TEXT_MAX_CHARS = 1200
        private const val MIN_SCORE = 0.25
        private const val MIN_MARGIN = 0.05

        fun fallbackSkillFromIntentType(intentType: IntentType): String {
            return when (intentType) {
                IntentType.SUMMARIZE -> "summarize_v1"
                IntentType.EXTRACT -> "extract_v1"
                IntentType.ASK_AGENT -> "agent_v1"
            }
        }
    }
}
