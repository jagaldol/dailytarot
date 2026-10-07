package com.jagaldol.dailytarot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jagaldol.dailytarot.ui.TodayScreen
import com.jagaldol.dailytarot.ui.theme.DailytarotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as TarotApplication).repository
        setContent {
            val state by repository.state.collectAsState()
            DailytarotTheme {
                TodayScreen(state, repository::selectCard, repository::setReversed, repository::retry)
            }
        }
    }
}
