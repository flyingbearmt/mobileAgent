package com.example.mobileagent.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.mobileagent.AppConfig
import com.example.mobileagent.agent.ChatApi
import com.example.mobileagent.agent.ChatMessage
import com.example.mobileagent.agent.ClientContext
import com.example.mobileagent.agent.ToolExecutor
import com.example.mobileagent.agent.ToolInfo
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

private val ISO8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)

private val MOCK_CONTEXT_BASE = ClientContext(
    userId = "bd0388aab9774492b1f24d12a8ca9b81",
    authToken = "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IkU3YTdiRWp6UGhmT1RZTUZTZUkwTSJ9.eyJodHRwOi8vb25lcGVsb3Rvbi5jb20vdXNlcl9pZCI6ImJkMDM4OGFhYjk3NzQ0OTJiMWYyNGQxMmE4Y2E5YjgxIiwiaXNzIjoiaHR0cHM6Ly9hdXRoLm9uZXBlbG90b24uY29tLyIsInN1YiI6ImF1dGgwfGJkMDM4OGFhYjk3NzQ0OTJiMWYyNGQxMmE4Y2E5YjgxIiwiYXVkIjpbImh0dHBzOi8vYXBpLm9uZXBlbG90b24uY29tLyIsImh0dHBzOi8vcGVsb3Rvbi1wcm9kLm9uZXBlbG90b24uYXV0aDAuY29tL3VzZXJpbmZvIl0sImlhdCI6MTc4MDQzNjAzMywiZXhwIjoxNzgwNjA4ODMzLCJzY29wZSI6Im9wZW5pZCBwcm9maWxlIGVtYWlsIHBlbG90b24tYXBpLm1lbWJlcnM6ZGVmYXVsdCBvZmZsaW5lX2FjY2VzcyIsImF6cCI6IldWb0p4VkRkUG9GeDRSTmV3dnZnNmNoMm1aN2J3bnNNIn0.kFhScKn00md6wmdRtEv8hAivcYNT7s-tyxyvK7bbNGsf6QByvJbK9k2UGtFe9PynokFbsG1KAWg952eC1F1N5csGdEJADAd_tPAvN-fXeFvKT9bZzPKYORHHZO-GnOUbowUR12wC1WnUaPV_FH9SNkfgI3Eqb7LRVRX-0r2gNcA3-BiE-0eUFhnWjkOvZ1lXbJIuqMSkwENME_V3kKkPd5SOYsFOZINHAGZtwr1VwRj6Fl-_VFh2M8rxkAegj-YfZq0M1Q-S9tWk-rjTuFTEFc0uxp69HG4uIuODZ_82KQJsN3xadBcbdNjVIXHBr76Zz4gcCJ2_FMLa1T94AZr63g",
    platform = AppConfig.PLATFORM,
    locale = "en_US",
    timezone = TimeZone.getDefault().id,
)

private fun mockContext() = MOCK_CONTEXT_BASE.copy(
    currentTime = ISO8601.apply { timeZone = TimeZone.getDefault() }.format(Date()),
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(bottomPadding: Dp = 0.dp) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = remember { ChatApi(AppConfig.CHAT_BASE_URL) }
    val toolExecutor = remember { ToolExecutor(context) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    val input = remember { mutableStateOf("") }
    val loading = remember { mutableStateOf(false) }
    val serverOnline = remember { mutableStateOf<Boolean?>(null) }
    val tools = remember { mutableStateOf<List<ToolInfo>>(emptyList()) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        serverOnline.value = runCatching { api.health() }.getOrDefault(false)
        runCatching { api.getTools(AppConfig.PLATFORM) }
            .onSuccess { tools.value = it }
            .onFailure { Log.e("CHATAPI", "getTools failed: ${it.message}") }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val text = input.value.trim().takeIf { it.isNotBlank() } ?: return
        input.value = ""
        messages.add(ChatMessage(role = "user", content = text))
        val assistantIndex = messages.size
        messages.add(ChatMessage(role = "assistant", content = ""))
        scope.launch {
            loading.value = true
            try {
                val history = messages.subList(0, assistantIndex)
                    .filter { it.content.isNotBlank() }
                    .takeLast(4)
                api.streamMessage(
                    messages = history,
                    toolNames = tools.value.map { it.name },
                    context = mockContext(),
                    toolExecutor = toolExecutor::execute,
                )
                    .catch { err -> messages[assistantIndex] = ChatMessage(role = "assistant", content = "Error: ${err.message}") }
                    .collect { token ->
                        val current = messages[assistantIndex].content
                        messages[assistantIndex] = ChatMessage(role = "assistant", content = current + token)
                    }
            } finally {
                loading.value = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                actions = {
                    Text(
                        text = when (serverOnline.value) {
                            null -> "⏳"
                            true -> "🟢"
                            false -> "🔴"
                        },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding(), bottom = bottomPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { /* top spacer */ }
                items(messages) { msg -> Bubble(msg) }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = input.value,
                    onValueChange = { input.value = it },
                    placeholder = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    enabled = !loading.value,
                )
                Button(
                    onClick = { send() },
                    enabled = input.value.isNotBlank() && !loading.value,
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = msg.content,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
