package com.example.mobileagent.entry

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mobileagent.AppConfig
import com.example.mobileagent.context.ContextCollector
import com.example.mobileagent.intent.IntentRuleEngine
import com.example.mobileagent.intent.IntentType
import com.example.mobileagent.agent.AgentApi
import com.example.mobileagent.task.TaskPollWorker
import com.example.mobileagent.ui.theme.MobileAgentTheme
import kotlinx.coroutines.launch

class ShareEntryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        maybeRequestNotifications()

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val sourceApp = extractSourceApp(intent)
        val suggestion = IntentRuleEngine.classify(sharedText.orEmpty())

        setContent {
            MobileAgentTheme {
                BottomSheetConfirm(
                    sharedText = sharedText,
                    suggestion = suggestion,
                    onCancel = { finish() },
                    onConfirm = { instruction ->
                        lifecycleScope.launch {
                            val ctx = ContextCollector.collect(
                                context = this@ShareEntryActivity,
                                sourceApp = sourceApp,
                                sharedText = sharedText,
                            )

                            val taskId = AgentApi(AppConfig.GATEWAY_BASE_URL)
                                .createTask(instruction = instruction, context = ctx)

                            enqueuePoll(taskId)
                            finish()
                        }
                    },
                )
            }
        }
    }

    private fun enqueuePoll(taskId: String) {
        val work = OneTimeWorkRequestBuilder<TaskPollWorker>()
            .setInputData(TaskPollWorker.inputData(taskId, AppConfig.GATEWAY_BASE_URL))
            .build()
        WorkManager.getInstance(this).enqueue(work)
    }

    private fun extractSourceApp(intent: Intent): String? {
        val referrer = intent.getParcelableExtra<Uri>(Intent.EXTRA_REFERRER)
        if (referrer != null && referrer.scheme == "android-app") {
            return referrer.schemeSpecificPart
        }
        return intent.`package`
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
}
