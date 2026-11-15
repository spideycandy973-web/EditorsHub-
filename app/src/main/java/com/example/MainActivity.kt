package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.EditorScreen
import com.example.ui.HomeScreen
import com.example.ui.MotionStudioViewModel
import com.example.ui.theme.OmsColors
import com.example.ui.theme.OpenMotionTheme
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object EditorRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenMotionTheme {
                OpenMotionApp()
            }
        }
    }
}

@Composable
fun OpenMotionApp(
    viewModel: MotionStudioViewModel = viewModel()
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val toastMsg by viewModel.toastMessage.collectAsState()

    LaunchedEffect(toastMsg) {
        val msg = toastMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    Scaffold(
        containerColor = OmsColors.Bg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        viewModel = viewModel,
                        onOpenEditor = {
                            navController.navigate(EditorRoute) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable<EditorRoute> {
                    EditorScreen(
                        viewModel = viewModel,
                        onBackHome = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
