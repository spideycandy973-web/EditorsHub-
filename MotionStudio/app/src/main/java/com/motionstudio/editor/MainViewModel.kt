package com.motionstudio.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionstudio.editor.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MainUiState(
    val darkTheme: Boolean = true,
    val dynamicColor: Boolean = false,
    val uiScale: Float = 1f,
    val language: String = "system"
)

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = settings.settings
        .map { MainUiState(it.darkTheme, it.dynamicColor, it.uiScale, it.language) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())
}
