package com.example.mobileagent.intent

enum class IntentType {
    SUMMARIZE,
    EXTRACT,
    ASK_AGENT,
}

object IntentRuleEngine {
    fun classify(text: String): IntentType {
        return when {
            text.contains("http", ignoreCase = true) -> IntentType.SUMMARIZE
            text.length > 500 -> IntentType.EXTRACT
            else -> IntentType.ASK_AGENT
        }
    }
}
