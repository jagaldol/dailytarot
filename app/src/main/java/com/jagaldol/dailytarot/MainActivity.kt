package com.jagaldol.dailytarot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jagaldol.dailytarot.ui.TodayScreen
import com.jagaldol.dailytarot.ui.theme.DailytarotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DailytarotTheme {
                TodayScreen()
            }
        }
    }
}
