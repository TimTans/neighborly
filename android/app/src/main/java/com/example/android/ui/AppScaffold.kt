package com.example.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.android.data.model.RecipeSuggestion
import com.example.android.domain.preferences.toRecipeRequestPayload
import com.example.android.ui.home.HomeScreen
import com.example.android.ui.home.RecipeDetailScreen
import com.example.android.ui.lists.GroceryListScreen
import com.example.android.ui.preferences.PreferencesScreen
import com.example.android.ui.route.RouteScreen
import com.example.android.ui.theme.NeighborlyColors
import com.example.android.viewmodel.home.HomeViewModel
import com.example.android.viewmodel.login.LoginViewModel
import com.example.android.viewmodel.route.RouteViewModel
import com.example.android.viewmodel.shopper.ShopperViewModel
import kotlinx.coroutines.delay

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Lists("Lists", Icons.Filled.List, Icons.Outlined.List),
    Route("Route", Icons.Rounded.Send, Icons.Outlined.Send)
}

private enum class AppDestination {
    Home,
    Lists,
    Route,
    Preferences,
    RecipeDetail
}

@Composable
fun AppScaffold(
    loginViewModel: LoginViewModel,
    homeViewModel: HomeViewModel,
    shopperViewModel: ShopperViewModel,
    routeViewModel: RouteViewModel
) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Home) }
    // RecipeSuggestion isn't @Parcelize-friendly, so the open recipe doesn't
    // survive process death — matching iOS, which also recomputes on cold start.
    var openedRecipe by remember { mutableStateOf<RecipeSuggestion?>(null) }

    val preferences = shopperViewModel.uiState.preferences
    LaunchedEffect(preferences) {
        // Soft debounce so a slider drag doesn't fire a network call per tick.
        // The repository also short-circuits on unchanged payloads, so this is
        // belt-and-suspenders. Mirrors iOS HomeViewModel.swift:56-74.
        delay(300)
        homeViewModel.refreshRecipe(preferences.toRecipeRequestPayload())
    }

    val offTabDestinations = setOf(AppDestination.Preferences, AppDestination.RecipeDetail)

    val currentTab = when (destination) {
        AppDestination.Home -> MainTab.Home
        AppDestination.Lists -> MainTab.Lists
        AppDestination.Route -> MainTab.Route
        AppDestination.Preferences -> MainTab.Home
        AppDestination.RecipeDetail -> MainTab.Home
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = NeighborlyColors.Surface) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab && destination !in offTabDestinations,
                        onClick = {
                            destination = when (tab) {
                                MainTab.Home -> AppDestination.Home
                                MainTab.Lists -> AppDestination.Lists
                                MainTab.Route -> AppDestination.Route
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == tab && destination !in offTabDestinations) {
                                    tab.selectedIcon
                                } else {
                                    tab.unselectedIcon
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeighborlyColors.Green,
                            selectedTextColor = NeighborlyColors.Green,
                            unselectedIconColor = NeighborlyColors.NavigationUnselected,
                            unselectedTextColor = NeighborlyColors.NavigationUnselected,
                            indicatorColor = NeighborlyColors.GreenSoft
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        when (destination) {
            AppDestination.Home -> HomeScreen(
                viewModel = homeViewModel,
                displayName = loginViewModel.uiState.value.displayName,
                initials = loginViewModel.uiState.value.initials,
                groceryListItemCount = shopperViewModel.uiState.groceryList.size,
                activeRoute = routeViewModel.uiState.optimizedRoute,
                onOpenPreferences = { destination = AppDestination.Preferences },
                onSignOut = loginViewModel::signOut,
                onStartTrip = { destination = AppDestination.Route },
                onRefreshRecipe = {
                    homeViewModel.retryRecipe(preferences.toRecipeRequestPayload())
                },
                onRetryRecipe = {
                    homeViewModel.retryRecipe(preferences.toRecipeRequestPayload())
                },
                onOpenRecipe = { recipe ->
                    openedRecipe = recipe
                    destination = AppDestination.RecipeDetail
                },
                modifier = Modifier.padding(innerPadding)
            )

            AppDestination.Lists -> GroceryListScreen(
                shopperViewModel = shopperViewModel,
                routeViewModel = routeViewModel,
                onNavigateToRoute = { destination = AppDestination.Route },
                modifier = Modifier.padding(innerPadding)
            )

            AppDestination.Route -> RouteScreen(
                routeViewModel = routeViewModel,
                shopperViewModel = shopperViewModel,
                modifier = Modifier.padding(innerPadding)
            )

            AppDestination.Preferences -> PreferencesScreen(
                shopperViewModel = shopperViewModel,
                onBack = { destination = AppDestination.Home },
                modifier = Modifier.padding(innerPadding)
            )

            AppDestination.RecipeDetail -> {
                val recipe = openedRecipe
                if (recipe != null) {
                    RecipeDetailScreen(
                        recipe = recipe,
                        onBack = {
                            openedRecipe = null
                            destination = AppDestination.Home
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    // Defensive: if saved-state restored RecipeDetail without a
                    // recipe held in memory, bounce back to Home.
                    LaunchedEffect(Unit) { destination = AppDestination.Home }
                }
            }
        }
    }
}
