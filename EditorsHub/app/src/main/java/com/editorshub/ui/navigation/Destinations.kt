package com.editorshub.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/** All six main destinations are real screens with real content. */
enum class MainTab(val route: String, val label: String, val icon: ImageVector, val order: Int) {
    HOME("tab_home", "Home", Icons.Default.Home, 0),
    PROJECTS("tab_projects", "Projects", Icons.Default.FolderOpen, 1),
    TEMPLATES("tab_templates", "Templates", Icons.Default.Dashboard, 2),
    EFFECTS("tab_effects", "Effects", Icons.Default.AutoAwesome, 3),
    AI("tab_ai", "AI Studio", Icons.Default.Bolt, 4),
    SETTINGS("tab_settings", "Settings", Icons.Default.Settings, 5);

    companion object {
        fun fromRoute(route: String?): MainTab = entries.firstOrNull { it.route == route } ?: HOME
    }
}

object Routes {
    const val MAIN = "main"
    const val EDITOR = "editor/{projectId}"
    const val TUTORIALS = "tutorials"
    const val IMPORT = "import"
    const val NODE_EDITOR = "node_editor"
    const val PROJECT_INFO = "project_info/{projectId}"
    fun editor(id: String) = "editor/$id"
    fun projectInfo(id: String) = "project_info/$id"
}
