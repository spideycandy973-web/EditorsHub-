package com.editorshub

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.editorshub.ui.EditorsHubRoot
import com.editorshub.ui.theme.LanguageManager
import com.editorshub.ui.theme.EditorsHubTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_EditorsHub)
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = hiltViewModel()
            val state by vm.uiState.collectAsState()
            LaunchedEffect(state.language) { LanguageManager.apply(state.language) }
            EditorsHubTheme(
                darkTheme = state.darkTheme,
                dynamicColor = state.dynamicColor,
                uiScale = state.uiScale
            ) {
                EditorsHubRoot()
            }
        }
    }
}
