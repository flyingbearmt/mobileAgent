package com.example.mobileagent.entry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mobileagent.intent.IntentType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetConfirm(
    sharedText: String?,
    suggestion: IntentType,
    onCancel: () -> Unit,
    onConfirm: (instruction: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val instruction = remember { mutableStateOf(defaultInstruction(suggestion, sharedText)) }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "Suggested: ${suggestion.name}", style = MaterialTheme.typography.titleMedium)
            Text(text = instruction.value, modifier = Modifier.padding(top = 12.dp))

            Button(
                onClick = { onConfirm(instruction.value) },
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
            ) {
                Text(text = "Confirm")
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
            ) {
                Text(text = "Cancel")
            }
        }
    }
}

private fun defaultInstruction(suggestion: IntentType, sharedText: String?): String {
    val t = sharedText.orEmpty()
    return when (suggestion) {
        IntentType.SUMMARIZE -> "Summarize this: $t"
        IntentType.EXTRACT -> "Extract key points: $t"
        IntentType.ASK_AGENT -> "Help with: $t"
    }
}
