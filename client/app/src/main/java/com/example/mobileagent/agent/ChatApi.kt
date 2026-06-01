package com.example.mobileagent.agent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ChatApi(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${baseUrl}health").get().build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    suspend fun sendMessage(message: String, sessionId: String): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("message", message)
            put("session_id", sessionId)
        }

        val req = Request.Builder()
            .url("${baseUrl}agent")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        Log.d("CHATAPI", "req: $req")

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            Log.d("CHATAPI", "resp: $body")
            if (!resp.isSuccessful) throw IllegalStateException("agent failed: ${resp.code} $body")
            JSONObject(body).getString("reply")
        }
    }
}

data class ChatMessage(val role: String, val content: String)
