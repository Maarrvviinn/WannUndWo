package de.marvin.wannundwo.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import de.marvin.wannundwo.ui.screens.haushalt.HaushaltTab
import kotlinx.coroutines.launch

enum class HomeTab { ANSTEHEND, VERLAUF, HAUSHALT }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCreateMember: () -> Unit,
    onNavigateToEditPermissions: (String) -> Unit,
    onNavigateToEditAbholung: (String) -> Unit,
    onNeedsHaushalt: () -> Unit,
    onLogout: () -> Unit
) {
    val vm: HomeViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val tabs = HomeTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val selectedTab = tabs[pagerState.currentPage]

    LaunchedEffect(uiState.needsHaushalt) {
        if (uiState.needsHaushalt) onNeedsHaushalt()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.haushalt?.name ?: "Wann & Wo") },
                actions = {
                    BadgedBox(badge = {
                        if (uiState.unreadCount > 0) Badge { Text(uiState.unreadCount.toString()) }
                    }) {
                        IconButton(onClick = onNavigateToNotifications) {
                            Icon(Icons.Default.Notifications, contentDescription = "Benachrichtigungen")
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedTextColor = Color.White,
                            unselectedIconColor = Color.White
                        ),
                        icon = {
                            when (tab) {
                                HomeTab.ANSTEHEND -> Icon(Icons.Default.Home, null)
                                HomeTab.VERLAUF -> Icon(Icons.Default.History, null)
                                HomeTab.HAUSHALT -> Icon(Icons.Default.Group, null)
                            }
                        },
                        label = {
                            when (tab) {
                                HomeTab.ANSTEHEND -> Text("Abholungen")
                                HomeTab.VERLAUF -> Text("Verlauf")
                                HomeTab.HAUSHALT -> Text("Haushalt")
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            val canCreate = uiState.currentUser?.permissions?.canCreateAbholung ?: false
            if (selectedTab == HomeTab.ANSTEHEND && canCreate) {
                FloatingActionButton(onClick = onNavigateToCreate) {
                    Icon(Icons.Default.Add, contentDescription = "Abholung erstellen")
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) { page ->
            when (tabs[page]) {
                HomeTab.ANSTEHEND -> AnstehendeAbholungenTab(
                    abholungen = uiState.abholungen,
                    members = uiState.members,
                    currentUser = uiState.currentUser,
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { vm.refresh() },
                    onDetail = onNavigateToDetail,
                    onEdit = onNavigateToEditAbholung,
                    onDelete = { vm.deleteAbholung(it) },
                    onRespond = { id, status, note -> vm.respondToAbholung(id, status, note) }
                )
                HomeTab.VERLAUF -> VerlaufTab(
                    abholungen = uiState.history,
                    members = uiState.members,
                    onDetail = onNavigateToDetail
                )
                HomeTab.HAUSHALT -> HaushaltTab(
                    haushalt = uiState.haushalt,
                    members = uiState.members,
                    currentUserId = uiState.currentUser?.id ?: "",
                    onCreateMember = onNavigateToCreateMember,
                    onEditPermissions = onNavigateToEditPermissions
                )
            }
        }
    }
}
