package com.example.mobileagent.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.mobileagent.actions.ActionIntentFactory
import com.example.mobileagent.result.ActionItem
import com.example.mobileagent.result.StructuredResultParser
import com.example.mobileagent.ui.theme.MobileAgentTheme

class ResultViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val resultText = intent.getStringExtra(EXTRA_RESULT_TEXT).orEmpty()

        setContent {
            MobileAgentTheme {
                ResultViewer(
                    taskId = taskId,
                    resultText = resultText,
                    onLaunchIntent = { intent -> ContextCompat.startActivity(this, intent, null) },
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_RESULT_TEXT = "result_text"
    }
}

@Composable
private fun ResultViewer(
    taskId: String,
    resultText: String,
    onLaunchIntent: (Intent) -> Unit,
    onClose: () -> Unit,
) {
    val structured = remember(resultText) { StructuredResultParser.parseOrNull(resultText) }
    val pendingAction = remember { mutableStateOf<ActionItem?>(null) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Text(text = "Task: $taskId", style = MaterialTheme.typography.titleMedium)

            if (structured == null) {
                Text(text = resultText, modifier = Modifier.padding(top = 12.dp))
            } else {
                if (!structured.summary.isNullOrBlank()) {
                    Text(text = "Summary", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    Text(text = structured.summary, modifier = Modifier.padding(top = 6.dp))
                }
                if (structured.todos.isNotEmpty()) {
                    Text(text = "Todos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    structured.todos.forEach { todo ->
                        Text(text = "- ${todo.text}", modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (!structured.answer.isNullOrBlank()) {
                    Text(text = "Answer", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    Text(text = structured.answer, modifier = Modifier.padding(top = 6.dp))
                }

                if (structured.actions.isNotEmpty()) {
                    Text(text = "Actions", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    structured.actions.forEach { action ->
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            Button(onClick = { pendingAction.value = action }) {
                                Text(text = action.type)
                            }
                        }
                    }
                }
            }

            OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
                Text(text = "Close")
            }
        }
    }

    val action = pendingAction.value
    if (action != null) {
        ActionConfirmSheet(
            action = action,
            onDismiss = { pendingAction.value = null },
            onLaunchIntent = { intent ->
                onLaunchIntent(intent)
                pendingAction.value = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionConfirmSheet(
    action: ActionItem,
    onDismiss: () -> Unit,
    onLaunchIntent: (Intent) -> Unit,
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val intent = remember(action) { buildIntentOrNull(action) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "Confirm action", style = MaterialTheme.typography.titleMedium)
            Text(text = action.type, modifier = Modifier.padding(top = 12.dp))

            val detail = actionDetail(action)
            if (detail.isNotBlank()) {
                Text(text = detail, modifier = Modifier.padding(top = 8.dp))
            }

            if (intent == null) {
                Text(
                    text = "Missing required fields for this action.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { onLaunchIntent(intent!!) },
                enabled = intent != null,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            ) {
                Text(text = "Continue")
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp).fillMaxWidth()) {
                Text(text = "Cancel")
            }
        }
    }
}

private fun buildIntentOrNull(action: ActionItem): Intent? {
    return when (action.type) {
        "DIAL" -> {
            val number = action.number ?: return null
            ActionIntentFactory.createDialIntent(number)
        }
        "SEND_SMS" -> {
            val to = action.to ?: return null
            ActionIntentFactory.createSendSmsIntent(to, action.body)
        }
        "CREATE_CALENDAR_EVENT" -> {
            ActionIntentFactory.createCalendarInsertIntent(action.title, action.startAt, action.endAt)
        }
        else -> null
    }
}

private fun actionDetail(action: ActionItem): String {
    return when (action.type) {
        "DIAL" -> "number=${action.number.orEmpty()}"
        "SEND_SMS" -> {
            val to = action.to.orEmpty()
            val body = action.body.orEmpty()
            if (body.isBlank()) "to=$to" else "to=$to\nbody=$body"
        }
        "CREATE_CALENDAR_EVENT" -> {
            val title = action.title.orEmpty()
            val start = action.startAt.orEmpty()
            val end = action.endAt.orEmpty()
            listOf(
                "title=$title",
                "start_at=$start",
                "end_at=$end",
            ).joinToString("\n")
        }
        else -> ""
    }
}
