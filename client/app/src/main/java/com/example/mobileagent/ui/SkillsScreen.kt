package com.example.mobileagent.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mobileagent.AppConfig
import com.example.mobileagent.agent.AgentApi
import com.example.mobileagent.agent.SkillDraft
import com.example.mobileagent.agent.SkillInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(bottomPadding: Dp = 0.dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { AgentApi(AppConfig.GATEWAY_BASE_URL) }

    val skills = remember { mutableStateOf<List<SkillInfo>>(emptyList()) }
    val loading = remember { mutableStateOf(false) }
    val editor = remember { mutableStateOf<SkillEditorState?>(null) }
    val deleteConfirm = remember { mutableStateOf<SkillInfo?>(null) }

    fun refresh() {
        scope.launch {
            loading.value = true
            runCatching {
                api.listSkills()
            }.onSuccess { list ->
                skills.value = list
            }.onFailure { err ->
                Toast.makeText(context, "Failed to load skills: ${err.message}", Toast.LENGTH_LONG).show()
            }
            loading.value = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Skills") },
                actions = {
                    IconButton(
                        onClick = {
                            editor.value = SkillEditorState(
                                mode = SkillEditorMode.Create,
                                name = "",
                                systemPrompt = "",
                                userPromptTemplate = "",
                            )
                        },
                    ) {
                        Text(text = "+", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = { refresh() }) {
                        Text(text = if (loading.value) "..." else "↻")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(top = innerPadding.calculateTopPadding(), bottom = bottomPadding).padding(horizontal = 12.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(skills.value, key = { it.name }) { skill ->
                    SkillTile(
                        skill = skill,
                        onEdit = {
                            editor.value = SkillEditorState(
                                mode = SkillEditorMode.Edit,
                                name = skill.name,
                                systemPrompt = skill.systemPrompt,
                                userPromptTemplate = skill.userPromptTemplate.orEmpty(),
                            )
                        },
                        onDelete = {
                            deleteConfirm.value = skill
                        },
                    )
                }
            }
        }
    }

    val editorState = editor.value
    if (editorState != null) {
        SkillEditorDialog(
            state = editorState,
            onDismiss = { editor.value = null },
            onSave = { draft ->
                scope.launch {
                    val result = when (editorState.mode) {
                        SkillEditorMode.Create -> runCatching { api.createSkill(draft) }
                        SkillEditorMode.Edit -> runCatching { api.upsertSkill(draft) }
                    }

                    result.onSuccess {
                        Toast.makeText(context, "Saved ${it.name}", Toast.LENGTH_SHORT).show()
                        editor.value = null
                        refresh()
                    }.onFailure { err ->
                        Toast.makeText(context, "Save failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    val deleteSkill = deleteConfirm.value
    if (deleteSkill != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirm.value = null },
            title = { Text(text = "Delete skill") },
            text = { Text(text = "Delete ${deleteSkill.name}?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                api.deleteSkill(deleteSkill.name)
                            }.onSuccess { ok ->
                                if (ok) {
                                    Toast.makeText(context, "Deleted ${deleteSkill.name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Not deleted", Toast.LENGTH_SHORT).show()
                                }
                                deleteConfirm.value = null
                                refresh()
                            }.onFailure { err ->
                                Toast.makeText(context, "Delete failed: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = deleteSkill.editable,
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirm.value = null }) {
                    Text(text = "Cancel")
                }
            },
        )
    }
}

@Composable
private fun SkillTile(
    skill: SkillInfo,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = skill.name, fontWeight = FontWeight.SemiBold)
            Text(text = "source=${skill.source}")
            Text(text = if (skill.editable) "editable" else "read-only")

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onEdit, enabled = skill.editable) {
                    Text(text = "Edit")
                }
                OutlinedButton(onClick = onDelete, enabled = skill.editable) {
                    Text(text = "Delete")
                }
            }
        }
    }
}

enum class SkillEditorMode {
    Create,
    Edit,
}

data class SkillEditorState(
    val mode: SkillEditorMode,
    val name: String,
    val systemPrompt: String,
    val userPromptTemplate: String,
)

@Composable
private fun SkillEditorDialog(
    state: SkillEditorState,
    onDismiss: () -> Unit,
    onSave: (SkillDraft) -> Unit,
) {
    val name = remember(state) { mutableStateOf(state.name) }
    val systemPrompt = remember(state) { mutableStateOf(state.systemPrompt) }
    val userPromptTemplate = remember(state) { mutableStateOf(state.userPromptTemplate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (state.mode == SkillEditorMode.Create) "New skill" else "Edit skill") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    enabled = state.mode == SkillEditorMode.Create,
                    label = { Text(text = "Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = systemPrompt.value,
                    onValueChange = { systemPrompt.value = it },
                    label = { Text(text = "System prompt") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    minLines = 4,
                )

                OutlinedTextField(
                    value = userPromptTemplate.value,
                    onValueChange = { userPromptTemplate.value = it },
                    label = { Text(text = "User prompt template") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    minLines = 6,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        SkillDraft(
                            name = name.value.trim(),
                            systemPrompt = systemPrompt.value,
                            userPromptTemplate = userPromptTemplate.value,
                        )
                    )
                },
                enabled = name.value.trim().isNotBlank(),
            ) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}
