package com.motionstudio.editor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.motionstudio.editor.ui.feature.editor.EditorScreen
import com.motionstudio.editor.ui.feature.home.*

@Composable
fun MotionStudioRoot() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenProject = { id -> nav.navigate("editor/$id") },
                onTemplates = { nav.navigate("templates") },
                onSettings = { nav.navigate("settings") },
                onTutorials = { nav.navigate("tutorials") }
            )
        }
        composable("templates") {
            TemplatesScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id -> nav.navigate("editor/$id") }
            )
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable("tutorials") { TutorialsScreen(onBack = { nav.popBackStack() }) }
        composable(
            route = "editor/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) {
            EditorScreen(onBack = { nav.popBackStack() })
        }
    }
}
