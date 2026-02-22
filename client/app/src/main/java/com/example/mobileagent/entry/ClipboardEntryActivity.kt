package com.example.mobileagent.entry

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mobileagent.AppConfig
import com.example.mobileagent.agent.AgentApi
import com.example.mobileagent.context.ContextCollector
import com.example.mobileagent.intent.IntentRuleEngine
import com.example.mobileagent.task.TaskPollWorker
import com.example.mobileagent.ui.theme.MobileAgentTheme
import kotlinx.coroutines.launch

class ClipboardEntryActivity : ComponentActivity() {

    private var sharedText: String? by mutableStateOf(null)
    private var sourceApp: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        maybeRequestNotifications()

        sharedText = intent.getStringExtra(EXTRA_CLIPBOARD_TEXT)
        sourceApp = intent.getStringExtra(EXTRA_SOURCE_APP)

        setContent {
            val suggestion = IntentRuleEngine.classify(sharedText.orEmpty())
            MobileAgentTheme {
                BottomSheetConfirm(
                    sharedText = sharedText,
                    suggestion = suggestion,
                    onCancel = { finish() },
                    onConfirm = { instruction ->
                        lifecycleScope.launch {
                            runCatching {
                                val ctx = ContextCollector.collect(
                                    context = this@ClipboardEntryActivity,
                                    sourceApp = sourceApp,
                                    sharedText = sharedText,
                                )

                                val taskId = AgentApi(AppConfig.GATEWAY_BASE_URL)
                                    .createTask(instruction = instruction, context = ctx)

                                enqueuePoll(taskId)
                            }.onFailure { err ->
                                Toast.makeText(
                                    this@ClipboardEntryActivity,
                                    "Failed to create task: ${err.message}",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }

                            finish()
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val current = sharedText
        if (!current.isNullOrBlank()) return

        val text = readClipboardText(this)
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "Clipboard is empty or not accessible", Toast.LENGTH_LONG).show()
            return
        }
        sharedText = text
    }

    private fun enqueuePoll(taskId: String) {
        val work = OneTimeWorkRequestBuilder<TaskPollWorker>()
            .setInputData(TaskPollWorker.inputData(taskId, AppConfig.GATEWAY_BASE_URL))
            .build()
        WorkManager.getInstance(this).enqueue(work)
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    companion object {
        const val EXTRA_CLIPBOARD_TEXT = "clipboard_text"
        const val EXTRA_SOURCE_APP = "source_app"

        fun createIntent(context: Context, clipboardText: String?, sourceApp: String?): Intent {
            return Intent(context, ClipboardEntryActivity::class.java)
                .putExtra(EXTRA_CLIPBOARD_TEXT, clipboardText)
                .putExtra(EXTRA_SOURCE_APP, sourceApp)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        fun readClipboardText(context: Context): String? {
            return runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if (!cm.hasPrimaryClip()) return null
                val clip: ClipData = cm.primaryClip ?: return null
                if (clip.itemCount <= 0) return null
                val item = clip.getItemAt(0)

                val direct = item.text?.toString()?.takeIf { it.isNotBlank() }
                if (direct != null) return direct

                val coerced = item.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
                if (coerced.equals("not available", ignoreCase = true)) return null
                coerced
            }.getOrNull()
        }
    }
}
