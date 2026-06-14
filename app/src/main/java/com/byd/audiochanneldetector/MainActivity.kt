package com.byd.audiochanneldetector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.byd.audiochanneldetector.ui.screens.MainScreen
import com.byd.audiochanneldetector.ui.theme.BYDAudioChannelDetectorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-Edge 模式：内容延伸到系统栏下方，由各 Composable 通过
        // Modifier.windowInsetsPadding 或 Scaffold 的 contentWindowInsets 控制内边距
        // （替代旧的 setStatusBarColor + WindowCompat.setDecorFitsSystemWindows 模式，
        // 后者自 SDK 35 起被官方标记为 deprecation 路径）。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BYDAudioChannelDetectorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}