package de.marvin.wannundwo.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import de.marvin.wannundwo.ui.screens.auth.HaushaltSetupScreen
import de.marvin.wannundwo.ui.screens.auth.LoginScreen
import de.marvin.wannundwo.ui.screens.home.HomeScreen
import de.marvin.wannundwo.ui.screens.abholung.CreateAbholungScreen
import de.marvin.wannundwo.ui.screens.abholung.AbholungDetailScreen
import de.marvin.wannundwo.ui.screens.haushalt.CreateMemberScreen
import de.marvin.wannundwo.ui.screens.haushalt.EditPermissionsScreen
import de.marvin.wannundwo.ui.screens.notifications.NotificationsScreen
import de.marvin.wannundwo.ui.screens.settings.SettingsScreen
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AppNavGraph(navController: NavHostController, isDarkMode: Boolean, onToggleTheme: (Boolean) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val startDestination = if (auth.currentUser != null) Screen.Home.route else Screen.Login.route

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.HaushaltSetup.route) {
            val email = auth.currentUser?.email ?: ""
            HaushaltSetupScreen(
                userEmail = email,
                onSetupComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.HaushaltSetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCreate = { navController.navigate(Screen.CreateAbholung.createRoute()) },
                onNavigateToDetail = { id -> navController.navigate(Screen.AbholungDetail.createRoute(id)) },
                onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToCreateMember = { navController.navigate(Screen.CreateMember.route) },
                onNavigateToEditPermissions = { uid -> navController.navigate(Screen.EditPermissions.createRoute(uid)) },
                onNavigateToEditAbholung = { id -> navController.navigate(Screen.CreateAbholung.createRoute(id)) },
                onNeedsHaushalt = {
                    navController.navigate(Screen.HaushaltSetup.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onLogout = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.CreateAbholung.route) { backStack ->
            val abholungId = backStack.arguments?.getString("abholungId")
            CreateAbholungScreen(
                abholungId = abholungId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AbholungDetail.route) { backStack ->
            val id = backStack.arguments?.getString("abholungId") ?: return@composable
            AbholungDetailScreen(
                abholungId = id,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Screen.CreateAbholung.createRoute(id)) }
            )
        }

        composable(Screen.CreateMember.route) {
            CreateMemberScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.EditPermissions.route) { backStack ->
            val userId = backStack.arguments?.getString("userId") ?: return@composable
            EditPermissionsScreen(userId = userId, onBack = { navController.popBackStack() })
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { id ->
                    navController.navigate(Screen.AbholungDetail.createRoute(id))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme,
                onLogout = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                },
                onAddAccount = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                },
                onAccountSwitched = {
                    navController.navigate(Screen.Home.route) { popUpTo(0) { inclusive = true } }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
