package com.example.mobileagent.context

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.Locale

object ContextCollector {
    fun collect(
        context: Context,
        sourceApp: String?,
        sharedText: String?,
    ): Map<String, Any?> {
        val deviceState = mapOf(
            "network" to getNetworkType(context)
        )

        return mapOf(
            "sourceApp" to sourceApp,
            "sharedText" to sharedText,
            "timestamp" to System.currentTimeMillis(),
            "locale" to Locale.getDefault().toLanguageTag(),
            "deviceState" to deviceState,
        )
    }

    private fun getNetworkType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "offline"
        val caps = cm.getNetworkCapabilities(network) ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
            else -> "other"
        }
    }
}
