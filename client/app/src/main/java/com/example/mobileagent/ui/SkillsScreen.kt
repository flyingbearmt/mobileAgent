package com.example.mobileagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.mobileagent.AppConfig
import com.example.mobileagent.agent.ChatApi
import com.example.mobileagent.agent.ToolInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(bottomPadding: Dp = 0.dp) {
    val scope = rememberCoroutineScope()
    val api = remember { ChatApi(AppConfig.CHAT_BASE_URL) }

    val tools = remember { mutableStateOf<List<ToolInfo>>(emptyList()) }
    val loading = remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading.value = true
            runCatching { api.getTools("android") }
                .onSuccess { tools.value = it }
                .onFailure { android.util.Log.e("CHATAPI", "getTools failed: ${it.message}") }
            loading.value = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Tools") },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Text(if (loading.value) "..." else "↻")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding(), bottom = bottomPadding)
                .padding(horizontal = 12.dp),
        ) {
            if (tools.value.isEmpty() && !loading.value) {
                Text(
                    text = "No tools available",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(tools.value, key = { it.name }) { tool ->
                        ToolTile(tool)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolTile(tool: ToolInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = tool.name, fontWeight = FontWeight.SemiBold)
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
