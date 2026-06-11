package de.marvin.wannundwo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import de.marvin.wannundwo.data.UserPreferences
import de.marvin.wannundwo.navigation.AppNavGraph
import de.marvin.wannundwo.navigation.Screen
import de.marvin.wannundwo.service.WannUndWoMessagingService
import de.marvin.wannundwo.update.DownloadService
import de.marvin.wannundwo.update.DownloadState
import de.marvin.wannundwo.update.GitHubUpdateManager
import de.marvin.wannundwo.update.ReleaseInfo
import de.marvin.wannundwo.update.UpdateScreen
import de.marvin.wannundwo.ui.theme.WannUndWoTheme
import de.marvin.wannundwo.util.observeNetworkConnectivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Create notification channel before any message can arrive
        WannUndWoMessagingService.ensureChannelExists(this)
        val prefs = UserPreferences(this)
        val initialDark = runBlocking { prefs.isDarkMode.first() }
        setContent {
            val context = LocalContext.current
            var isDarkMode by remember { mutableStateOf(initialDark) }
            var pendingUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
            val downloadState by DownloadService.state.collectAsState()

            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                pendingUpdate = GitHubUpdateManager.checkForUpdate(context, BuildConfig.VERSION_CODE)
            }

            WannUndWoTheme(darkTheme = isDarkMode) {
                val isOnline by context.observeNetworkConnectivity()
                    .collectAsState(initial = true)

                // ── Notification permission ────────────────────────────
                NotificationPermissionHandler()
                val deepLinkId = remember { intent?.getStringExtra("abholungId") }

                pendingUpdate?.let { release ->
                    AlertDialog(
                        onDismissRequest = { pendingUpdate = null },
                        title = { androidx.compose.material3.Text("Neue Version verfügbar") },
                        text = {
                            androidx.compose.material3.Text(
                                "Version ${release.tagName} ist verfügbar. Die App bleibt nach dem Update eingeloggt."
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                val toDownload = release
                                pendingUpdate = null
                                DownloadService.start(context, toDownload)
                            }) {
                                androidx.compose.material3.Text("Installieren")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { pendingUpdate = null }) {
                                androidx.compose.material3.Text("Später")
                            }
                        }
                    )
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()

                        // Navigate to detail when app launched via push notification
                        LaunchedEffect(deepLinkId) {
                            if (!deepLinkId.isNullOrBlank()) {
                                navController.navigate(Screen.AbholungDetail.createRoute(deepLinkId)) {
                                    launchSingleTop = true
                                }
                            }
                        }
                        AppNavGraph(
                            navController = navController,
                            isDarkMode = isDarkMode,
                            onToggleTheme = { dark ->
                                isDarkMode = dark
                                scope.launch { prefs.setDarkMode(dark) }
                            }
                        )

                        // UpdateScreen overlay — shown during download/install
                        val ds = downloadState
                        if (ds !is DownloadState.Idle) {
                            val release = when (ds) {
                                is DownloadState.Downloading -> ds.release
                                is DownloadState.Installing -> ds.release
                                is DownloadState.Error -> ds.release
                                else -> null
                            }
                            if (release != null) {
                                UpdateScreen(
                                    release = release,
                                    progress = when (ds) {
                                        is DownloadState.Downloading -> ds.progress
                                        is DownloadState.Installing -> 1f
                                        else -> 0f
                                    },
                                    isDone = ds is DownloadState.Installing,
                                    error = (ds as? DownloadState.Error)?.message,
                                    onRetry = { DownloadService.start(context, release) },
                                    onDismiss = { DownloadService.reset() }
                                )
                            }
                        }

                        // Offline banner — shown on top of all content
                        AnimatedVisibility(
                            visible = !isOnline,
                            enter = expandVertically(expandFrom = Alignment.Top),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(10f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.error)
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                androidx.compose.foundation.layout.Column {
                                    Text(
                                        text = "Kein Internet",
                                        color = MaterialTheme.colorScheme.onError,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Angezeigte Daten könnten veraltet sein.",
                                        color = MaterialTheme.colorScheme.onError.copy(alpha = 0.85f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Handles POST_NOTIFICATIONS permission on Android 13+.
 *
 * Flow:
 *  1. Not granted → request immediately via system dialog.
 *  2. Denied (can ask again) → show rationale dialog explaining why, then re-request on confirm.
 *  3. Permanently denied ("Don't ask again") → show dialog with button that opens app settings.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun NotificationPermissionHandler() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val permissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

    // true = show rationale dialog; false = show settings dialog
    var showRationale by remember { mutableStateOf(false) }
    var showSettingsPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(permissionState.status) {
        when (val s = permissionState.status) {
            is PermissionStatus.Granted -> { /* nothing to do */ }
            is PermissionStatus.Denied -> {
                if (s.shouldShowRationale) {
                    showRationale = true
                } else {
                    // First launch: request directly. Permanently denied: open settings.
                    // Accompanist fires launchPermissionRequest() safely.
                    permissionState.launchPermissionRequest()
                }
            }
        }
    }

    // Rationale dialog — user denied once but can still be asked again
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            icon = { Icon(Icons.Default.NotificationsOff, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Benachrichtigungen aktivieren") },
            text = {
                Text(
                    "Wann & Wo benötigt Benachrichtigungen, um dich über neue Abholungen und Statusänderungen zu informieren. " +
                    "Ohne diese Erlaubnis verpasst du möglicherweise wichtige Updates."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showRationale = false
                    permissionState.launchPermissionRequest()
                }) { Text("Erlauben") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Nicht jetzt") }
            }
        )
    }

    // Settings dialog — user permanently denied
    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = { showSettingsPrompt = false },
            icon = { Icon(Icons.Default.NotificationsOff, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Benachrichtigungen deaktiviert") },
            text = {
                Text(
                    "Du hast Benachrichtigungen dauerhaft deaktiviert. " +
                    "Öffne die App-Einstellungen, um sie manuell zu aktivieren."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showSettingsPrompt = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }) { Text("Einstellungen öffnen") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsPrompt = false }) { Text("Ignorieren") }
            }
        )
    }

    // When user returns from settings, re-check the permission
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // If still denied after returning from settings, show prompt again
                if (permissionState.status is PermissionStatus.Denied) {
                    val denied = permissionState.status as PermissionStatus.Denied
                    if (!denied.shouldShowRationale) {
                        showSettingsPrompt = true
                    }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
