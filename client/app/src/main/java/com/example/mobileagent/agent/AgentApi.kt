package com.example.mobileagent.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AgentApi(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun createTask(instruction: String, context: Map<String, Any?>): String {
        val payload = JSONObject()
        payload.put("instruction", instruction)
        payload.put("context", JSONObject(context))
        payload.put("capabilities", JSONObject())

        val req = Request.Builder()
            .url("$baseUrl/v1/tasks")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("createTask failed: ${resp.code} $body")
            }
            val json = JSONObject(body)
            return json.getString("task_id")
        }
    }

    fun getTask(taskId: String): TaskResponse {
        val req = Request.Builder()
            .url("$baseUrl/v1/tasks/$taskId")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("getTask failed: ${resp.code} $body")
            }
            val json = JSONObject(body)
            val status = json.optString("status")
            val stage = json.optString("stage")
            val progress = json.optDouble("progress")

            val resultObj = json.optJSONObject("result")
            val resultText = resultObj?.optString("text")

            val errorObj = json.optJSONObject("error")
            val errorMessage = errorObj?.optString("message")

            return TaskResponse(
                taskId = json.optString("task_id"),
                status = status,
                stage = stage,
                progress = progress,
                resultText = resultText,
                errorMessage = errorMessage,
            )
        }
    }
}

data class TaskResponse(
    val taskId: String,
    val status: String,
    val stage: String,
    val progress: Double,
    val resultText: String?,
    val errorMessage: String?,
)
