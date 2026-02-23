package com.example.mobileagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mobileagent.ui.SkillsScreen
import com.example.mobileagent.ui.theme.MobileAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileAgentTheme {
                SkillsScreen()
            }
        }
    }
}