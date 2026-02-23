package com.example.mobileagent.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class AgentApi(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun createTask(
        instruction: String,
        context: Map<String, Any?>,
        capabilities: Map<String, Any?> = emptyMap(),
    ): String =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
            payload.put("instruction", instruction)
            payload.put("context", JSONObject(context))
            payload.put("capabilities", JSONObject(capabilities))

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
                json.getString("task_id")
            }
        }

    suspend fun getTask(taskId: String): TaskResponse = withContext(Dispatchers.IO) {
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

            TaskResponse(
                taskId = json.optString("task_id"),
                status = status,
                stage = stage,
                progress = progress,
                resultText = resultText,
                errorMessage = errorMessage,
            )
        }
    }

    suspend fun listSkills(): List<SkillInfo> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/skills")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("listSkills failed: ${resp.code} $body")
            }
            val json = JSONObject(body)
            val arr = json.optJSONArray("skills") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        SkillInfo(
                            name = o.optString("name"),
                            source = o.optString("source"),
                            editable = o.optBoolean("editable"),
                            routingText = o.optString("routing_text").takeIf { it.isNotBlank() },
                            systemPrompt = o.optString("system_prompt"),
                            userPromptTemplate = o.optString("user_prompt_template").takeIf { it.isNotBlank() },
                        )
                    )
                }
            }
        }
    }

    suspend fun upsertSkill(skill: SkillDraft): SkillInfo = withContext(Dispatchers.IO) {
        val payload = JSONObject()
        payload.put("name", skill.name)
        payload.put("system_prompt", skill.systemPrompt)
        payload.put("user_prompt_template", skill.userPromptTemplate)

        val url = "$baseUrl/v1/skills/${skill.name}"
        val req = Request.Builder()
            .url(url)
            .put(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("upsertSkill failed: ${resp.code} $body")
            }
            val o = JSONObject(body)
            SkillInfo(
                name = o.optString("name"),
                source = o.optString("source"),
                editable = o.optBoolean("editable"),
                routingText = o.optString("routing_text").takeIf { it.isNotBlank() },
                systemPrompt = o.optString("system_prompt"),
                userPromptTemplate = o.optString("user_prompt_template").takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun createSkill(skill: SkillDraft): SkillInfo = withContext(Dispatchers.IO) {
        val payload = JSONObject()
        payload.put("name", skill.name)
        payload.put("system_prompt", skill.systemPrompt)
        payload.put("user_prompt_template", skill.userPromptTemplate)

        val req = Request.Builder()
            .url("$baseUrl/v1/skills")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("createSkill failed: ${resp.code} $body")
            }
            val o = JSONObject(body)
            SkillInfo(
                name = o.optString("name"),
                source = o.optString("source"),
                editable = o.optBoolean("editable"),
                routingText = o.optString("routing_text").takeIf { it.isNotBlank() },
                systemPrompt = o.optString("system_prompt"),
                userPromptTemplate = o.optString("user_prompt_template").takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun deleteSkill(name: String): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/v1/skills/$name")
            .delete()
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("deleteSkill failed: ${resp.code} $body")
            }
            val json = JSONObject(body)
            json.optBoolean("deleted", false)
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

data class SkillInfo(
    val name: String,
    val source: String,
    val editable: Boolean,
    val routingText: String?,
    val systemPrompt: String,
    val userPromptTemplate: String?,
)

data class SkillDraft(
    val name: String,
    val systemPrompt: String,
    val userPromptTemplate: String,
)
