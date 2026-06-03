package com.example.mobileagent.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

class ToolExecutor(private val context: Context) {

    suspend fun execute(name: String, input: JSONObject): String {
        Log.d("TOOL", "execute $name input=$input")
        return when (name) {
            "open_app" -> openApp(input)
            "get_weather" -> getWeather(input)
            else -> """{"error":"unknown tool $name"}"""
        }
    }

    private fun openApp(input: JSONObject): String {
        val bundleId = input.optString("bundle_id").trim()
        if (bundleId.isEmpty()) return """{"error":"bundle_id is required"}"""

        val intent = resolveIntent(bundleId)
            ?: fuzzyLaunchIntent(bundleId)
            ?: return """{"error":"app not found: $bundleId"}"""

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            """{"opened":true,"bundle_id":"$bundleId"}"""
        } catch (e: Exception) {
            """{"error":"${e.message}","bundle_id":"$bundleId"}"""
        }
    }

    private fun resolveIntent(bundleId: String): Intent? {
        val key = bundleId.lowercase()
        // Keyword aliases → exact intent
        val aliasIntent = when {
            key == "settings" || key.endsWith(".settings") -> Intent(Settings.ACTION_SETTINGS)
            key == "phone" || key.endsWith(".dialer") -> Intent(Intent.ACTION_DIAL)
            key == "maps" || key.contains("maps") -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
            key == "camera" || key.endsWith(".camera") -> Intent("android.media.action.IMAGE_CAPTURE")
            key == "browser" || key.endsWith(".chrome") || key.endsWith(".browser") ->
                Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
            else -> null
        }
        if (aliasIntent != null) return aliasIntent

        // Common package ID normalizations
        val candidates = buildList {
            add(bundleId)
            // com.google.maps → com.google.android.apps.maps
            if (key == "com.google.maps") add("com.google.android.apps.maps")
            if (key.startsWith("com.google.") && !key.startsWith("com.google.android."))
                add(key.replace("com.google.", "com.google.android.apps."))
            // bare names → try common roots
            if (!key.contains(".")) {
                add("com.$key")
                add("com.$key.android")
                add("com.google.android.apps.$key")
            }
        }
        return candidates.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
    }

    // Last resort: scan installed packages for one whose ID contains the query fragment
    private fun fuzzyLaunchIntent(query: String): Intent? {
        val q = query.lowercase().replace(".", "")
        return context.packageManager
            .getInstalledApplications(0)
            .firstOrNull { it.packageName.lowercase().replace(".", "").contains(q) }
            ?.let { context.packageManager.getLaunchIntentForPackage(it.packageName) }
    }

    private fun getWeather(input: JSONObject): String {
        val location = input.optString("location", "").trim()
        // Open the system weather app or Google search for weather
        val query = if (location.isNotEmpty()) "weather in $location" else "weather"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            """{"opened":true,"query":"$query"}"""
        } catch (e: Exception) {
            """{"error":"${e.message}"}"""
        }
    }
}
