package com.example.mobileagent.result

import org.json.JSONArray
import org.json.JSONObject

data class StructuredResult(
    val version: Int,
    val summary: String?,
    val todos: List<TodoItem>,
    val answer: String?,
    val actions: List<ActionItem>,
)

data class TodoItem(
    val text: String,
    val dueAt: String?,
)

data class ActionItem(
    val type: String,
    val title: String?,
    val startAt: String?,
    val endAt: String?,
    val to: String?,
    val body: String?,
    val number: String?,
)

object StructuredResultParser {
    fun parseOrNull(rawText: String): StructuredResult? {
        val json = runCatching { JSONObject(rawText) }.getOrNull() ?: return null
        val version = json.optInt("version", -1)
        if (version != 1) return null

        val todos = parseTodos(json.optJSONArray("todos"))
        val actions = parseActions(json.optJSONArray("actions"))

        return StructuredResult(
            version = version,
            summary = json.optString("summary").takeIf { it.isNotBlank() },
            todos = todos,
            answer = json.optString("answer").takeIf { it.isNotBlank() },
            actions = actions,
        )
    }

    fun extractPreviewText(rawText: String): String {
        val parsed = parseOrNull(rawText) ?: return rawText
        return parsed.summary
            ?: parsed.answer
            ?: parsed.todos.firstOrNull()?.text
            ?: rawText
    }

    private fun parseTodos(arr: JSONArray?): List<TodoItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val text = o.optString("text").takeIf { it.isNotBlank() } ?: continue
                add(
                    TodoItem(
                        text = text,
                        dueAt = o.optString("due_at").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    private fun parseActions(arr: JSONArray?): List<ActionItem> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val type = o.optString("type").takeIf { it.isNotBlank() } ?: continue
                add(
                    ActionItem(
                        type = type,
                        title = o.optString("title").takeIf { it.isNotBlank() },
                        startAt = o.optString("start_at").takeIf { it.isNotBlank() },
                        endAt = o.optString("end_at").takeIf { it.isNotBlank() },
                        to = o.optString("to").takeIf { it.isNotBlank() },
                        body = o.optString("body").takeIf { it.isNotBlank() },
                        number = o.optString("number").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }
}
