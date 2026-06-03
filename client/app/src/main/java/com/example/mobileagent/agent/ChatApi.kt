package com.example.mobileagent.agent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ToolInfo(val name: String, val description: String)

data class ClientContext(
    val userId: String,
    val authToken: String,
    val platform: String = "tablet",
    val locale: String = "en_US",
    val timezone: String = "UTC",
    val currentTime: String = "",
)

data class ChatMessage(val role: String, val content: String)

class ChatApi(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) {
    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${baseUrl}/health").get().build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    suspend fun getTools(platform: String = "tablet"): List<ToolInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${baseUrl}/tools?platform=$platform").get().build()
        Log.d("CHATAPI", "getTools: $req")
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("tools")
                ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(ToolInfo(name = o.optString("name"), description = o.optString("description")))
                }
            }
        }
    }

    fun streamMessage(
        messages: List<ChatMessage>,
        toolNames: List<String> = emptyList(),
        context: ClientContext? = null,
    ): Flow<String> = flow {
        val payload = buildPayload(messages.map { msg ->
            JSONObject().apply { put("role", msg.role); put("content", msg.content) }
        }, toolNames, context)

        val req = Request.Builder()
            .url("${baseUrl}/agent/stream")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Log.d("CHATAPI", "stream req: $req")

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                throw IllegalStateException("/agent/stream failed: ${resp.code} $body")
            }
            val source = resp.body?.source() ?: return@use
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val json = runCatching { JSONObject(line.removePrefix("data: ").trim()) }.getOrNull() ?: continue
                when (json.optString("type")) {
                    "done" -> break
                    "delta" -> json.optString("text").takeIf { it.isNotEmpty() }?.let { emit(it) }
                    "tool_use" -> {
                        val name = json.optString("tool_name")
                        val input = json.optJSONObject("tool_input")?.toString() ?: "{}"
                        Log.d("TOOL", "$name → $input")
                        emit("\n[tool: $name $input]\n")
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun buildPayload(
        apiMessages: List<JSONObject>,
        toolNames: List<String>,
        context: ClientContext?,
    ): JSONObject = JSONObject().apply {
        val arr = JSONArray()
        apiMessages.forEach { arr.put(it) }
        put("messages", arr)
        if (toolNames.isNotEmpty()) put("tool_names", JSONArray(toolNames))
        if (context != null) {
            put("client_context", JSONObject().apply {
                put("user_id", context.userId)
                put("auth_token", context.authToken)
                put("platform", context.platform)
                put("locale", context.locale)
                put("timezone", context.timezone)
                if (context.currentTime.isNotEmpty()) put("current_time", context.currentTime)
            })
        }
    }
}
