package de.marvin.wannundwo.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object HaushaltSetup : Screen("haushalt_setup")
    object Home : Screen("home")
    object CreateAbholung : Screen("create_abholung?abholungId={abholungId}") {
        fun createRoute(abholungId: String? = null) =
            if (abholungId != null) "create_abholung?abholungId=$abholungId" else "create_abholung"
    }
    object AbholungDetail : Screen("abholung_detail/{abholungId}") {
        fun createRoute(id: String) = "abholung_detail/$id"
    }
    object CreateMember : Screen("create_member")
    object EditPermissions : Screen("edit_permissions/{userId}") {
        fun createRoute(userId: String) = "edit_permissions/$userId"
    }
    object Notifications : Screen("notifications")
    object Settings : Screen("settings")
}
