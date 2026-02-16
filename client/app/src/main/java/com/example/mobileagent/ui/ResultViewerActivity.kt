package com.example.mobileagent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mobileagent.ui.theme.MobileAgentTheme

class ResultViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val resultText = intent.getStringExtra(EXTRA_RESULT_TEXT).orEmpty()

        setContent {
            MobileAgentTheme {
                ResultViewer(taskId = taskId, resultText = resultText) { finish() }
            }
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_RESULT_TEXT = "result_text"
    }
}

@Composable
private fun ResultViewer(taskId: String, resultText: String, onClose: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            Text(text = "Task: $taskId", style = MaterialTheme.typography.titleMedium)
            Text(text = resultText, modifier = Modifier.padding(top = 12.dp))
            Button(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
                Text(text = "Close")
            }
        }
    }
}
