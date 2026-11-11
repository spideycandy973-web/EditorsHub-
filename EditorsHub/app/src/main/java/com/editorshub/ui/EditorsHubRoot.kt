package com.editorshub.ui

import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.editorshub.ui.feature.ai.AiStudioScreen
import com.editorshub.ui.feature.editor.EditorScreen
import com.editorshub.ui.feature.effects.EffectsLibraryScreen
import com.editorshub.ui.feature.home.*
import com.editorshub.ui.feature.importer.ImportScreen
import com.editorshub.ui.feature.node.NodeEditorScreen
import com.editorshub.ui.components.pressScale
import com.editorshub.ui.navigation.MainTab
import com.editorshub.ui.navigation.Routes
import com.editorshub.ui.theme.AppMotion
import com.editorshub.ui.theme.EditorDimens
import com.editorshub.ui.theme.EditorPalette
import com.editorshub.ui.theme.ReferencePalette

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EditorsHubRoot() {
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
        ) {
            EditorScreen(
                onBack = { rootNav.popBackStack() },
                onOpenImporter = { rootNav.navigate(Routes.IMPORT) }
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun MainShell(rootNav: androidx.navigation.NavHostController) {
    val tabNav = rememberNavController()
    val current by tabNav.currentBackStackEntryAsState()
    val tab = MainTab.fromRoute(current?.destination?.route)
    var createMode by remember { mutableStateOf<CreateMode?>(null) }
    val createVm: HomeViewModel = hiltViewModel()
    val onCreate: (CreateMode) -> Unit = { mode -> createMode = mode }
    val closeCreate: () -> Unit = { createMode = null }
    // Navigation rail on tablets / landscape, bottom bar on phones.
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    val openProject: (String) -> Unit = { id ->
        createMode = null
        rootNav.navigate(Routes.editor(id))
    }
    val goImport: () -> Unit = { rootNav.navigate(Routes.IMPORT) }

    LaunchedEffect(Unit) {
        createVm.openProject.collect { id ->
            createMode = null
            rootNav.navigate(Routes.editor(id))
        }
    }
    LaunchedEffect(current) { createMode = null }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            AnimatedNavigationRail(tabNav, tab) { createMode = CreateMode.PROJECT }
            Box(Modifier.weight(1f)) {
                MainContent(
                    tabNav = tabNav,
                    openProject = openProject,
                    goImport = goImport,
                    rootNav = rootNav,
                    onCreate = onCreate,
                    createMode = createMode,
                    createVm = createVm,
                    onCloseCreate = closeCreate
                )
            }
        }
    } else {
        Scaffold(
            containerColor = EditorPalette.Black,
            bottomBar = {
                AnimatedNavigationBar(
                    nav = tabNav,
                    current = tab,
                    createOpen = createMode != null,
                    onCreate = { createMode = CreateMode.PROJECT },
                    onClose = closeCreate
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                MainContent(
                    tabNav = tabNav,
                    openProject = openProject,
                    goImport = goImport,
                    rootNav = rootNav,
                    onCreate = onCreate,
                    createMode = createMode,
                    createVm = createVm,
                    onCloseCreate = closeCreate
                )
            }
        }
    }
}

/** The tab host plus the reference-style creation sheet overlay. */
@Composable
private fun MainContent(
    tabNav: androidx.navigation.NavHostController,
    openProject: (String) -> Unit,
    goImport: () -> Unit,
    rootNav: androidx.navigation.NavHostController,
    onCreate: (CreateMode) -> Unit,
    createMode: CreateMode?,
    createVm: HomeViewModel,
    onCloseCreate: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        TabHost(tabNav, openProject, goImport, rootNav, onCreate)
        createMode?.let { mode ->
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .clickable(onClick = onCloseCreate)
            )
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = 14.dp, end = 14.dp, bottom = 3.dp)
            ) {
                key(mode) {
                    ProjectCreationSheet(
                        vm = createVm,
                        initialMode = mode,
                        onDismiss = onCloseCreate,
                        onCreated = onCloseCreate
                    )
                }
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
    rootNav: androidx.navigation.NavHostController,
    onCreate: (CreateMode) -> Unit
) {
    NavHost(
        navController = tabNav,
        startDestination = MainTab.HOME.route,
        modifier = Modifier.fillMaxSize(),
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
                onSettings = { navigateToTab(tabNav, MainTab.SETTINGS) },
                onProjects = { navigateToTab(tabNav, MainTab.PROJECTS) }
            )
        }
        composable(MainTab.PROJECTS.route) {
            ProjectsScreen(
                onOpenProject = openProject,
                onImport = goImport,
                onNew = onCreate,
                onSettings = { tabNav.navigate(MainTab.SETTINGS.route) }
            )
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
            SettingsScreen(
                onBack = { tabNav.popBackStack() },
                onTutorials = { rootNav.navigate(Routes.TUTORIALS) },
                onAi = { tabNav.navigate(MainTab.AI.route) },
                onNodeEditor = { rootNav.navigate(Routes.NODE_EDITOR) }
            )
        }
    }
}

@Composable
private fun AnimatedNavigationBar(
    nav: androidx.navigation.NavHostController,
    current: MainTab,
    createOpen: Boolean,
    onCreate: () -> Unit,
    onClose: () -> Unit
) {
    val projectsSelected = current in setOf(MainTab.PROJECTS, MainTab.TEMPLATES, MainTab.EFFECTS, MainTab.AI)
    val createInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val rotation by animateFloatAsState(
        if (createOpen) 45f else 0f,
        animationSpec = tween(AppMotion.Fast), label = "create-fab-rotation"
    )
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(92.dp).background(ReferencePalette.Background),
        contentAlignment = Alignment.Center
    ) {
        // The reference pill is about 57.5% of the source device's width, capped
        // at ~254dp. The cap keeps it from stretching on unusually wide screens.
        val pillWidth = if (maxWidth * 0.575f < 254.dp) maxWidth * 0.575f else 254.dp
        Row(
            Modifier.width(pillWidth).height(69.dp)
                .clip(CircleShape)
                .background(if (createOpen) ReferencePalette.Card.copy(alpha = 0.68f) else ReferencePalette.Card)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainNavItem(MainTab.HOME, current == MainTab.HOME, Modifier.weight(1f)) {
                navigateToTab(nav, MainTab.HOME)
            }
            Box(
                Modifier.size(50.dp).rotate(rotation).pressScale(createInteraction, 0.9f)
                    .clip(CircleShape).background(ReferencePalette.Mint)
                    .clickable(
                        interactionSource = createInteraction,
                        indication = LocalIndication.current,
                        onClick = { if (createOpen) onClose() else onCreate() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    if (createOpen) "Close create project" else "Create project",
                    Modifier.size(26.dp), tint = ReferencePalette.Background
                )
            }
            MainNavItem(MainTab.PROJECTS, projectsSelected, Modifier.weight(1f)) {
                navigateToTab(nav, MainTab.PROJECTS)
            }
        }
    }
}

@Composable
private fun MainNavItem(tab: MainTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val iconColor by animateColorAsState(
        if (selected) ReferencePalette.Text else ReferencePalette.MutedText,
        animationSpec = tween(AppMotion.Fast), label = "${tab.route}-icon-color"
    )
    val labelColor by animateColorAsState(
        if (selected) ReferencePalette.Text else ReferencePalette.MutedText,
        animationSpec = tween(AppMotion.Fast), label = "${tab.route}-label-color"
    )
    Column(
        modifier.fillMaxHeight().pressScale(interaction, 0.93f)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(tab.icon, tab.label, Modifier.size(20.dp), tint = iconColor)
        Spacer(Modifier.height(2.dp))
        Text(tab.label, fontSize = 10.sp, color = labelColor, maxLines = 1)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedNavigationRail(
    nav: androidx.navigation.NavHostController,
    current: MainTab,
    onCreate: () -> Unit
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
        Spacer(Modifier.weight(1f))
        FloatingActionButton(
            onClick = onCreate,
            containerColor = ReferencePalette.Mint,
            modifier = Modifier.size(50.dp)
        ) {
            Icon(Icons.Default.Add, "Create project", Modifier.size(24.dp),
                tint = ReferencePalette.Background)
        }
        Spacer(Modifier.height(EditorDimens.Space4))
    }
}

private fun navigateToTab(nav: androidx.navigation.NavHostController, tab: MainTab) {
    nav.navigate(tab.route) {
        popUpTo(MainTab.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
