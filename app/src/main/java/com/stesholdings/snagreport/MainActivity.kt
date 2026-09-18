package com.stesholdings.snagreport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stesholdings.snagreport.ui.NEW_ENTRY_ID
import com.stesholdings.snagreport.ui.SnagViewModel
import com.stesholdings.snagreport.ui.entry.SnagEntryScreen
import com.stesholdings.snagreport.ui.list.SnagListScreen
import com.stesholdings.snagreport.ui.theme.SnaggingReportTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SnagViewModel by viewModels {
        SnagViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SnaggingReportTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SnaggingReportApp(viewModel)
                }
            }
        }
    }
}

private const val ROUTE_LIST = "list"
private const val ROUTE_ENTRY = "entry/{entryId}"
private const val ARG_ENTRY_ID = "entryId"

@Composable
fun SnaggingReportApp(viewModel: SnagViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_LIST) {
        composable(ROUTE_LIST) {
            SnagListScreen(
                viewModel = viewModel,
                onAddEntry = { navController.navigate("entry/$NEW_ENTRY_ID") },
                onOpenEntry = { id -> navController.navigate("entry/$id") }
            )
        }
        composable(
            route = ROUTE_ENTRY,
            arguments = listOf(navArgument(ARG_ENTRY_ID) { type = NavType.LongType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong(ARG_ENTRY_ID) ?: NEW_ENTRY_ID
            SnagEntryScreen(
                entryId = entryId,
                viewModel = viewModel,
                onDone = { navController.popBackStack() }
            )
        }
    }
}
