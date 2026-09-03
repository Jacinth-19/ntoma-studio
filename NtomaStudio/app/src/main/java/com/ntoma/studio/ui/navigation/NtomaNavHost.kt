package com.ntoma.studio.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.Scaffold
import com.ntoma.studio.ui.screens.create.CameraScreen
import com.ntoma.studio.ui.screens.create.CreateScreen
import com.ntoma.studio.ui.screens.create.EditImageScreen
import com.ntoma.studio.ui.screens.design.DesignDetailScreen
import com.ntoma.studio.ui.screens.discover.DiscoverScreen
import com.ntoma.studio.ui.screens.fabric.AnalysisScreen
import com.ntoma.studio.ui.screens.fabric.FabricDetailScreen
import com.ntoma.studio.ui.screens.favorites.FavoritesScreen
import com.ntoma.studio.ui.screens.history.HistoryScreen
import com.ntoma.studio.ui.screens.home.HomeScreen
import com.ntoma.studio.ui.screens.profile.AboutScreen
import com.ntoma.studio.ui.screens.profile.DataControlsScreen
import com.ntoma.studio.ui.screens.profile.HelpScreen
import com.ntoma.studio.ui.screens.profile.LegalScreen
import com.ntoma.studio.ui.screens.profile.PrivacyScreen
import com.ntoma.studio.ui.screens.profile.ProfileScreen
import com.ntoma.studio.ui.screens.profile.SettingsScreen
import com.ntoma.studio.ui.screens.recommendations.RecommendationsScreen
import com.ntoma.studio.ui.screens.result.ResultScreen
import com.ntoma.studio.ui.screens.tryon.TryOnScreen
import com.ntoma.studio.ui.screens.premium.PremiumScreen
import com.ntoma.studio.ui.screens.collections.CollectionsScreen
import com.ntoma.studio.ui.screens.collections.CollectionDetailScreen
import com.ntoma.studio.ui.screens.colormatch.ColorMatchScreen
import com.ntoma.studio.ui.screens.measurements.MeasurementsScreen
import com.ntoma.studio.ui.screens.tailors.TailorsScreen
import com.ntoma.studio.ui.screens.tailors.TailorDetailScreen
import com.ntoma.studio.ui.screens.wardrobe.WardrobeScreen

@Composable
fun MainScaffold(startWithScan: Boolean, onScanConsumed: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(startWithScan) {
        if (startWithScan) {
            navController.navigate(Routes.CAMERA_FABRIC)
            onScanConsumed()
        }
    }

    // Deep links from notifications: route "app://…" uris into the right screen, whether the
    // intent arrived with onCreate or with onNewIntent.
    val deepLink by com.ntoma.studio.DeepLinks.pending.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(deepLink) {
        val uri = deepLink ?: return@LaunchedEffect
        navController.handleDeepLink(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(uri),
                ctx,
                com.ntoma.studio.MainActivity::class.java,
            ),
        )
        com.ntoma.studio.DeepLinks.consume()
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in Routes.TOP_LEVEL) {
                NtomaBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { padding ->
        NtomaNavHost(navController, padding)
    }
}

@Composable
private fun NtomaNavHost(
    nav: NavHostController,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = androidx.compose.ui.Modifier,
        enterTransition = {
            androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) +
                androidx.compose.animation.slideInHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(220),
                ) { it / 12 }
        },
        exitTransition = {
            androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(180))
        },
        popEnterTransition = {
            androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(180))
        },
        popExitTransition = {
            androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(220)) +
                androidx.compose.animation.slideOutHorizontally(
                    animationSpec = androidx.compose.animation.core.tween(220),
                ) { it / 12 }
        },
    ) {
        composable(Routes.HOME) { HomeScreen(nav, padding) }
        composable(Routes.DISCOVER) { DiscoverScreen(nav, padding) }
        composable(Routes.CREATE) { CreateScreen(nav, padding) }
        composable(Routes.FAVORITES) { FavoritesScreen(nav, padding) }
        composable(Routes.PROFILE) { ProfileScreen(nav, padding) }

        composable(Routes.CAMERA_FABRIC) { CameraScreen(nav, padding, target = "fabric") }
        composable(Routes.CAMERA_PERSON) { CameraScreen(nav, padding, target = "person") }

        composable(
            "edit/{uri}/{target}",
            arguments = listOf(
                androidx.navigation.navArgument("uri") { type = NavType.StringType },
                androidx.navigation.navArgument("target") { type = NavType.StringType },
            ),
        ) { entry ->
            EditImageScreen(
                nav = nav,
                padding = padding,
                uri = Uri.decode(entry.arguments?.getString("uri")),
                target = entry.arguments?.getString("target") ?: "fabric",
            )
        }

        composable(
            "analysis/{uri}",
            arguments = listOf(androidx.navigation.navArgument("uri") { type = NavType.StringType }),
        ) { entry ->
            AnalysisScreen(nav, padding, uri = Uri.decode(entry.arguments?.getString("uri")))
        }

        composable(
            "fabric/{id}",
            arguments = listOf(androidx.navigation.navArgument("id") { type = NavType.LongType }),
            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "app://fabric/{id}" }),
        ) { entry ->
            FabricDetailScreen(nav, padding, fabricId = entry.arguments?.getLong("id") ?: 0)
        }

        composable(
            "recommendations/{fabricId}",
            arguments = listOf(androidx.navigation.navArgument("fabricId") { type = NavType.LongType }),
        ) { entry ->
            RecommendationsScreen(nav, padding, fabricId = entry.arguments?.getLong("fabricId") ?: 0)
        }

        composable(
            "design/{styleId}",
            arguments = listOf(androidx.navigation.navArgument("styleId") { type = NavType.StringType }),
            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "app://design/{styleId}" }),
        ) { entry ->
            DesignDetailScreen(nav, padding, styleId = entry.arguments?.getString("styleId").orEmpty())
        }

        composable(
            "tryon?fabricId={fabricId}&styleId={styleId}&person={person}",
            arguments = listOf(
                androidx.navigation.navArgument("fabricId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                androidx.navigation.navArgument("styleId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                androidx.navigation.navArgument("person") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            TryOnScreen(
                nav = nav,
                padding = padding,
                fabricId = entry.arguments?.getLong("fabricId")?.takeIf { it != -1L },
                styleId = entry.arguments?.getString("styleId")?.takeIf { it.isNotBlank() },
                personPath = entry.arguments?.getString("person")?.takeIf { it.isNotBlank() }?.let { Uri.decode(it) },
            )
        }

        composable(
            "result/{lookId}",
            arguments = listOf(androidx.navigation.navArgument("lookId") { type = NavType.LongType }),
            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "app://look/{lookId}" }),
        ) { entry ->
            ResultScreen(nav, padding, lookId = entry.arguments?.getLong("lookId") ?: 0)
        }

        composable(Routes.HISTORY) { HistoryScreen(nav, padding) }
        composable(Routes.SETTINGS) { SettingsScreen(nav, padding) }
        composable(Routes.PRIVACY) { PrivacyScreen(nav, padding) }
        composable(Routes.DATA_CONTROLS) { DataControlsScreen(nav, padding) }
        composable(Routes.HELP) { HelpScreen(nav, padding) }
        composable(Routes.ABOUT) { AboutScreen(nav, padding) }
        composable(Routes.PREMIUM) { PremiumScreen(nav, padding) }
        composable("legal/{doc}", arguments = listOf(androidx.navigation.navArgument("doc") { type = NavType.StringType })) { entry ->
            LegalScreen(nav, padding, doc = entry.arguments?.getString("doc") ?: "privacy")
        }

        composable(Routes.COLLECTIONS) { CollectionsScreen(nav, padding) }
        composable(
            "collection/{id}",
            arguments = listOf(androidx.navigation.navArgument("id") { type = NavType.LongType }),
            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "app://collection/{id}" }),
        ) { entry ->
            CollectionDetailScreen(nav, padding, collectionId = entry.arguments?.getLong("id") ?: 0)
        }
        composable(Routes.WARDROBE) { WardrobeScreen(nav, padding) }
        composable(
            "colormatch/{fabricId}",
            arguments = listOf(androidx.navigation.navArgument("fabricId") { type = NavType.LongType }),
        ) { entry ->
            ColorMatchScreen(nav, padding, fabricId = entry.arguments?.getLong("fabricId") ?: 0)
        }
        composable(Routes.TAILORS) { TailorsScreen(nav, padding) }
        composable(
            "tailor/{id}?styleId={styleId}&fabricId={fabricId}",
            arguments = listOf(
                androidx.navigation.navArgument("id") { type = NavType.StringType },
                androidx.navigation.navArgument("styleId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                androidx.navigation.navArgument("fabricId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { entry ->
            TailorDetailScreen(
                nav,
                padding,
                tailorId = entry.arguments?.getString("id").orEmpty(),
                preselectedStyleId = entry.arguments?.getString("styleId")?.takeIf { it.isNotBlank() },
                preselectedFabricId = entry.arguments?.getLong("fabricId")?.takeIf { it != -1L },
            )
        }
        composable(Routes.MEASUREMENTS) { MeasurementsScreen(nav, padding) }
    }
}
