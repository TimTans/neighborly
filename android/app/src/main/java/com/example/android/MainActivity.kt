package com.example.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.android.data.api.KtorNeighborlyApi
import com.example.android.data.connectivity.ConnectivityManagerNetworkMonitor
import com.example.android.data.local.SharedPreferencesGroceryListLocalDataSource
import com.example.android.data.local.preferences.SharedPreferencesPreferenceRepository
import com.example.android.data.repository.GroceryListRepository
import com.example.android.ui.AppScaffold
import com.example.android.ui.theme.NeighborlyTheme
import com.example.android.ui.login.LoginScreen
import com.example.android.viewmodel.home.HomeViewModel
import com.example.android.viewmodel.login.LoginViewModel
import com.example.android.viewmodel.route.RouteViewModel
import com.example.android.viewmodel.shopper.ShopperViewModel

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val routeViewModel: RouteViewModel by viewModels()
    private val shopperViewModel: ShopperViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application
                ShopperViewModel(
                    application = app,
                    groceryListRepository = GroceryListRepository(
                        SharedPreferencesGroceryListLocalDataSource(app.applicationContext)
                    ),
                    preferenceRepository = SharedPreferencesPreferenceRepository(app.applicationContext),
                    networkMonitor = ConnectivityManagerNetworkMonitor(app.applicationContext),
                    api = KtorNeighborlyApi()
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NeighborlyTheme {
                Surface {
                    val loginState by loginViewModel.uiState.collectAsState()
                    if (loginState.isLoggedIn) {
                        AppScaffold(
                            loginViewModel = loginViewModel,
                            homeViewModel = homeViewModel,
                            shopperViewModel = shopperViewModel,
                            routeViewModel = routeViewModel
                        )
                    } else {
                        LoginScreen(viewModel = loginViewModel)
                    }
                }
            }
        }
    }
}
