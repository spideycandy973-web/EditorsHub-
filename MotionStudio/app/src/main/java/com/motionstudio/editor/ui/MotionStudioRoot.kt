package com.motionstudio.editor.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.motionstudio.editor.ui.feature.ai.AiStudioScreen
import com.motionstudio.editor.ui.feature.editor.EditorScreen
import com.motionstudio.editor.ui.feature.effects.EffectsLibraryScreen
import com.motionstudio.editor.ui.feature.home.*
import com.motionstudio.editor.ui.feature.importer.ImportScreen
import com.motionstudio.editor.ui.feature.node.NodeEditorScreen
import com.motionstudio.editor.ui.navigation.MainTab
import com.motionstudio.editor.ui.navigation.Routes
import com.motionstudio.editor.ui.theme.AppMotion
import com.motionstudio.editor.ui.theme.EditorDimens
import com.motionstudio.editor.ui.theme.EditorPalette

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MotionStudioRoot() {
    val rootNav = rememberNavController()
    NavHost(
        navController = rootNav,
        startDestination = Routes.MAIN,
        enterTransition = {
            fadeIn(tween(AppMotion.Normal, easing = AppMotion.Standard)) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(AppMotion.Normal, easing = AppMotion.Emphasized),
                    initialOffset = { it / 10 }
                )
        },
        exitTransition = {
            fadeOut(tween(AppMotion.Fast, easing = AppMotion.Standard)) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(AppMotion.Normal, easing = AppMotion.Emphasized),
                    targetOffset = { it / 10 }
                )
        },
        popEnterTransition = {
            fadeIn(tween(AppMotion.Normal)) +
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(AppMotion.Normal, easing = AppMotion.Emphasized),
                    initialOffset = { it / 10 }
                )
        },
        popExitTransition = {
            fadeOut(tween(AppMotion.Fast)) +
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(AppMotion.Normal, easing = AppMotion.Emphasized),
                    targetOffset = { it / 10 }
                )
        }
    ) {
        composable(Routes.MAIN) { MainShell(rootNav) }
        composable(Routes.TUTORIALS) { TutorialsScreen(onBack = { rootNav.popBackStack() }) }
        composable(Routes.IMPORT) {
            ImportScreen(onBack = { rootNav.popBackStack() },
                onOpenProject = { id -> rootNav.navigate(Routes.editor(id)) })
        }
        composable(Routes.NODE_EDITOR) {
            NodeEditorScreen(onBack = { rootNav.popBackStack() },
                onOpenProject = { id -> rootNav.navigate(Routes.editor(id)) })
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { EditorScreen(onBack = { rootNav.popBackStack() }) }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun MainShell(rootNav: androidx.navigation.NavHostController) {
    val tabNav = rememberNavController()
    val current by tabNav.currentBackStackEntryAsState()
    val tab = MainTab.fromRoute(current?.destination?.route)
    // Navigation rail on tablets / landscape, bottom bar on phones.
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    val openProject: (String) -> Unit = { id -> rootNav.navigate(Routes.editor(id)) }
    val goImport: () -> Unit = { rootNav.navigate(Routes.IMPORT) }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            AnimatedNavigationRail(tabNav, tab)
            Box(Modifier.weight(1f)) { TabHost(tabNav, openProject, goImport, rootNav) }
        }
    } else {
        Scaffold(
            containerColor = EditorPalette.Black,
            bottomBar = { AnimatedNavigationBar(tabNav, tab) }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                TabHost(tabNav, openProject, goImport, rootNav)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun TabHost(
    tabNav: androidx.navigation.NavHostController,
    openProject: (String) -> Unit,
    goImport: () -> Unit,
    rootNav: androidx.navigation.NavHostController
) {
    NavHost(
        navController = tabNav,
        startDestination = MainTab.HOME.route,
        enterTransition = {
            val from = MainTab.fromRoute(initialState.destination.route).order
            val to = MainTab.fromRoute(targetState.destination.route).order
            val dir = if (to >= from) AnimatedContentTransitionScope.SlideDirection.Start
                else AnimatedContentTransitionScope.SlideDirection.End
            fadeIn(tween(AppMotion.Normal, easing = AppMotion.Standard)) +
                slideIntoContainer(dir, tween(AppMotion.Normal, easing = AppMotion.Emphasized),
                    initialOffset = { it / 14 })
        },
        exitTransition = {
            val from = MainTab.fromRoute(initialState.destination.route).order
            val to = MainTab.fromRoute(targetState.destination.route).order
            val dir = if (to >= from) AnimatedContentTransitionScope.SlideDirection.Start
                else AnimatedContentTransitionScope.SlideDirection.End
            fadeOut(tween(AppMotion.Fast)) +
                slideOutOfContainer(dir, tween(AppMotion.Normal, easing = AppMotion.Emphasized),
                    targetOffset = { it / 14 })
        },
        popEnterTransition = { fadeIn(tween(AppMotion.Normal)) },
        popExitTransition = { fadeOut(tween(AppMotion.Fast)) }
    ) {
        composable(MainTab.HOME.route) {
            HomeScreen(
                onOpenProject = openProject,
                onProjects = { tabNav.navigate(MainTab.PROJECTS.route) },
                onTemplates = { tabNav.navigate(MainTab.TEMPLATES.route) },
                onEffects = { tabNav.navigate(MainTab.EFFECTS.route) },
                onAi = { tabNav.navigate(MainTab.AI.route) },
                onSettings = { tabNav.navigate(MainTab.SETTINGS.route) },
                onTutorials = { rootNav.navigate(Routes.TUTORIALS) },
                onImport = goImport,
                onNodeEditor = { rootNav.navigate(Routes.NODE_EDITOR) }
            )
        }
        composable(MainTab.PROJECTS.route) {
            ProjectsScreen(onOpenProject = openProject, onImport = goImport, onNew = {
                tabNav.navigate(MainTab.HOME.route)
            })
        }
        composable(MainTab.TEMPLATES.route) {
            TemplatesScreen(onBack = { tabNav.popBackStack() }, onCreated = openProject)
        }
        composable(MainTab.EFFECTS.route) {
            EffectsLibraryScreen(onOpenProject = openProject,
                onNodeEditor = { rootNav.navigate(Routes.NODE_EDITOR) })
        }
        composable(MainTab.AI.route) {
            AiStudioScreen(onOpenProject = openProject,
                onSettings = { tabNav.navigate(MainTab.SETTINGS.route) })
        }
        composable(MainTab.SETTINGS.route) {
            SettingsScreen(onBack = { tabNav.popBackStack() },
                onTutorials = { rootNav.navigate(Routes.TUTORIALS) })
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedNavigationBar(
    nav: androidx.navigation.NavHostController,
    current: MainTab
) {
    NavigationBar(
        containerColor = EditorPalette.Charcoal,
        tonalElevation = 0.dp,
        modifier = Modifier.height(72.dp)
    ) {
        MainTab.entries.forEach { tab ->
            val selected = tab == current
            val scale by animateFloatAsState(if (selected) 1.16f else 1f, AppMotion.springSpec(), label = "nav-scale")
            val tint by animateColorAsState(
                if (selected) EditorPalette.Purple else EditorPalette.TextMuted,
                tween(AppMotion.Normal), label = "nav-tint"
            )
            NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(nav, tab) },
                icon = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
                        AnimatedVisibility(
                            visible = selected,
                            enter = scaleIn(tween(AppMotion.Normal)) + fadeIn(),
                            exit = scaleOut(tween(AppMotion.Fast)) + fadeOut()
                        ) {
                            Box(
                                Modifier.size(32.dp).background(
                                    Brush.radialGradient(listOf(Color(0x558B7BFF), Color.Transparent)),
                                    CircleShape
                                )
                            )
                        }
                        Icon(tab.icon, tab.label, Modifier.scale(scale).size(EditorDimens.Icon), tint = tint)
                    }
                },
                label = {
                    AnimatedContent(
                        targetState = selected,
                        transitionSpec = { fadeIn(tween(AppMotion.Fast)) togetherWith fadeOut(tween(AppMotion.Fast)) },
                        label = "nav-label"
                    ) { isSelected ->
                        Text(
                            tab.label,
                            fontSize = if (isSelected) 10.sp else 9.sp,
                            color = if (isSelected) EditorPalette.TextPrimary else EditorPalette.TextMuted,
                            maxLines = 1
                        )
                    }
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedNavigationRail(
    nav: androidx.navigation.NavHostController,
    current: MainTab
) {
    NavigationRail(
        containerColor = EditorPalette.Charcoal,
        modifier = Modifier.width(84.dp)
    ) {
        Spacer(Modifier.height(EditorDimens.Space4))
        MainTab.entries.forEach { tab ->
            val selected = tab == current
            val scale by animateFloatAsState(if (selected) 1.14f else 1f, AppMotion.springSpec(), label = "rail-scale")
            NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(nav, tab) },
                icon = {
                    AnimatedContent(targetState = selected, label = "rail-icon") { s ->
                        Icon(tab.icon, tab.label, Modifier.scale(scale).size(EditorDimens.Icon),
                            tint = if (s) EditorPalette.Purple else EditorPalette.TextMuted)
                    }
                },
                label = { Text(tab.label, fontSize = 9.sp, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(indicatorColor = EditorPalette.SurfaceVariant)
            )
        }
    }
}

private fun navigateToTab(nav: androidx.navigation.NavHostController, tab: MainTab) {
    nav.navigate(tab.route) {
        popUpTo(MainTab.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
