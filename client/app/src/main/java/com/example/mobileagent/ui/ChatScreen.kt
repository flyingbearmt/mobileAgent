package com.example.mobileagent.ui

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
import androidx.compose.ui.unit.dp
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
import com.example.mobileagent.agent.ChatApi
import com.example.mobileagent.agent.ChatMessage
import kotlinx.coroutines.launch
import java.util.UUID

private const val CHAT_BASE_URL = "https://hack-26-tablet-ai-agent.ge.stage.k8s.onepeloton.com/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(bottomPadding: Dp = 0.dp) {
    val scope = rememberCoroutineScope()
    val api = remember { ChatApi(CHAT_BASE_URL) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    val input = remember { mutableStateOf("") }
    val loading = remember { mutableStateOf(false) }
    val serverOnline = remember { mutableStateOf<Boolean?>(null) }
    val sessionId = remember { UUID.randomUUID().toString() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        serverOnline.value = runCatching { api.health() }.getOrDefault(false)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun send() {
        val text = input.value.trim().takeIf { it.isNotBlank() } ?: return
        input.value = ""
        messages.add(ChatMessage(role = "user", content = text))
        scope.launch {
            loading.value = true
            runCatching {
                api.sendMessage(text, sessionId)
            }.onSuccess { reply ->
                messages.add(ChatMessage(role = "assistant", content = reply))
            }.onFailure { err ->
                messages.add(ChatMessage(role = "assistant", content = "Error: ${err.message}"))
            }
            loading.value = false
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
                if (loading.value) {
                    item { Bubble(ChatMessage(role = "assistant", content = "…")) }
                }
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
