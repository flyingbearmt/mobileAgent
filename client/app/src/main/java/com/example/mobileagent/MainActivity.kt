package com.example.mobileagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.mobileagent.ui.ChatScreen
import com.example.mobileagent.ui.SkillsScreen
import com.example.mobileagent.ui.theme.MobileAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileAgentTheme {
                val tab = remember { mutableStateOf(0) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab.value == 0,
                                onClick = { tab.value = 0 },
                                label = { Text("Chat") },
                                icon = {},
                            )
                            NavigationBarItem(
                                selected = tab.value == 1,
                                onClick = { tab.value = 1 },
                                label = { Text("Skills") },
                                icon = {},
                            )
                        }
                    },
                ) { innerPadding ->
                    when (tab.value) {
                        0 -> ChatScreen(bottomPadding = innerPadding.calculateBottomPadding())
                        1 -> SkillsScreen(bottomPadding = innerPadding.calculateBottomPadding())
                    }
                }
            }
        }
    }
}
