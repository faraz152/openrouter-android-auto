package io.openrouter.android.auto.sample.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    val isConnected by viewModel.isConnected.collectAsState()

                    val startDestination = if (isConnected) "models" else "api_key"

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("api_key") {
                            ApiKeyScreen(
                                viewModel = viewModel,
                                onConnected = {
                                    navController.navigate("models") {
                                        popUpTo("api_key") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("models") {
                            ModelBrowserScreen(
                                viewModel = viewModel,
                                onChatClick = { modelId ->
                                    navController.navigate("chat/$modelId")
                                },
                                onCostClick = { modelId ->
                                    navController.navigate("cost/$modelId")
                                }
                            )
                        }
                        composable("chat/{modelId}") { backStackEntry ->
                            val modelId = backStackEntry.arguments?.getString("modelId") ?: return@composable
                            ChatScreen(viewModel = viewModel, modelId = modelId)
                        }
                        composable("cost/{modelId}") { backStackEntry ->
                            val modelId = backStackEntry.arguments?.getString("modelId") ?: return@composable
                            CostScreen(viewModel = viewModel, modelId = modelId)
                        }
                    }
                }
            }
        }
    }
}
