package com.ahmadarif.gdface.sample.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ahmadarif.gdface.sample.gdApp

private const val ENROLL_ROUTE = "enroll?personId={personId}"
private const val TRANSITION_MS = 150

private class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("enroll", "Enroll", Icons.Default.Add),
    Tab("recognize", "Recognize", Icons.Default.CenterFocusStrong),
    Tab("realtime", "Live", Icons.Default.Videocam),
    Tab("faces", "Faces", Icons.Default.Groups)
)

@Composable
fun AppRoot() {
    val app = LocalContext.current.gdApp
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { if (route == "home" || route == "faces") BottomBar(route, nav) }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (app.settings.onboarded) "home" else "welcome",
            modifier = Modifier.padding(padding),
            // The defaults are a 700 ms cross-fade, which makes every Back feel slow.
            enterTransition = { fadeIn(tween(TRANSITION_MS)) },
            exitTransition = { fadeOut(tween(TRANSITION_MS)) },
            popEnterTransition = { fadeIn(tween(TRANSITION_MS)) },
            popExitTransition = { fadeOut(tween(TRANSITION_MS)) }
        ) {
            composable("welcome") {
                WelcomeScreen {
                    app.settings.onboarded = true
                    nav.navigate("home") { popUpTo("welcome") { inclusive = true } }
                }
            }
            composable("home") { HomeScreen(nav) }
            composable(
                ENROLL_ROUTE,
                arguments = listOf(navArgument("personId") { type = NavType.StringType; defaultValue = "" })
            ) { EnrollScreen(nav, it.arguments?.getString("personId").orEmpty()) }
            composable(
                "enrolled/{personId}",
                arguments = listOf(navArgument("personId") { type = NavType.StringType })
            ) { EnrolledScreen(nav, it.arguments?.getString("personId").orEmpty()) }
            composable("recognize") { RecognizeScreen(nav) }
            composable("realtime") { RealtimeScreen(nav) }
            composable("faces") { FaceListScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("about") { AboutScreen(nav) }
        }
    }
}

@Composable
private fun BottomBar(current: String?, nav: NavController) {
    NavigationBar(containerColor = Gd.Surface, contentColor = Gd.TextSecondary) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Gd.Primary,
                    selectedTextColor = Gd.Primary,
                    indicatorColor = Gd.Primary.copy(alpha = 0.16f),
                    unselectedIconColor = Gd.TextSecondary,
                    unselectedTextColor = Gd.TextSecondary
                )
            )
        }
    }
}
