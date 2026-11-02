package com.motionstudio.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.motionstudio.editor.ui.MotionStudioRoot
import com.motionstudio.editor.ui.theme.MotionStudioTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MotionStudio)
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = hiltViewModel()
            val state by vm.uiState.collectAsState()
            MotionStudioTheme(
                darkTheme = state.darkTheme,
                dynamicColor = state.dynamicColor
            ) {
                MotionStudioRoot()
            }
        }
    }
}
