package com.lawrefbook.unified

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.lawrefbook.unified.ui.navigation.AppNav
import com.lawrefbook.unified.ui.theme.LawRefBookUnifiedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = (application as MyApplication).settings
        setContent {
            val dark by settings.darkMode.collectAsState(initial = false)
            val dynamic by settings.dynamicColor.collectAsState(initial = true)
            // 初始值须与 SettingsRepository.DEFAULT_SEED 一致，避免首帧颜色闪变
            val seed by settings.themeSeed.collectAsState(initial = 0xFF1565C0L)
            LawRefBookUnifiedTheme(darkTheme = dark, dynamicColor = dynamic, seed = seed) {
                AppNav()
            }
        }
    }
}
