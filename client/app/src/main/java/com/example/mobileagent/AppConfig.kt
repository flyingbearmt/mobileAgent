package com.example.mobileagent

import android.os.Build

object AppConfig {
    val GATEWAY_BASE_URL: String = if (isEmulator()) {
        "http://10.0.2.2:8001"
    } else {
        "http://127.0.0.1:8001"
    }

    val CHAT_BASE_URL: String = if (isEmulator()) {
        "http://10.0.2.2:8080"
    } else {
//        "http://10.0.2.2:8080"
        "https://hack-26-tablet-ai-agent.ge.onepeloton.com"
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        val brand = Build.BRAND
        val device = Build.DEVICE
        val product = Build.PRODUCT

        return fingerprint.startsWith("generic") || fingerprint.startsWith("unknown") ||
            model.contains("google_sdk") || model.contains("Emulator") || model.contains("Android SDK built for") ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product == "google_sdk"
    }
}
