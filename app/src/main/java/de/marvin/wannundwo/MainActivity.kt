package de.marvin.wannundwo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import de.marvin.wannundwo.data.UserPreferences
import de.marvin.wannundwo.navigation.AppNavGraph
import de.marvin.wannundwo.update.ApkInstaller
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        val prefs = UserPreferences(this)
        val initialDark = runBlocking { prefs.isDarkMode.first() }
        setContent {
            val context = LocalContext.current
            var isDarkMode by remember { mutableStateOf(initialDark) }
            var pendingUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
            var activeDownload by remember { mutableStateOf<ReleaseInfo?>(null) }
            var downloadProgress by remember { mutableFloatStateOf(-1f) }
            var downloadDone by remember { mutableStateOf(false) }
            var downloadError by remember { mutableStateOf<String?>(null) }

            val scope = rememberCoroutineScope()

            fun startDownload(release: ReleaseInfo) {
                activeDownload = release
                downloadProgress = -1f
                downloadDone = false
                downloadError = null
                scope.launch {
                    val apkFile = GitHubUpdateManager.downloadApk(context, release) { p ->
                        downloadProgress = p
                    }
                    if (apkFile != null) {
                        downloadDone = true
                        ApkInstaller.install(context, apkFile)
                    } else {
                        downloadError = "Download der neuen APK fehlgeschlagen."
                    }
                }
            }

            LaunchedEffect(Unit) {
                pendingUpdate = GitHubUpdateManager.checkForUpdate(context, BuildConfig.VERSION_CODE)
            }

            WannUndWoTheme(darkTheme = isDarkMode) {
                val isOnline by context.observeNetworkConnectivity()
                    .collectAsState(initial = true)

                pendingUpdate?.let { release ->
                    AlertDialog(
                        onDismissRequest = { pendingUpdate = null },
                        title = { androidx.compose.material3.Text("Neue Version verfügbar") },
                        text = {
                            androidx.compose.material3.Text(
                                "Version ${release.versionName} (${release.tagName}) ist verfügbar. Die App bleibt nach dem Update eingeloggt."
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                val toDownload = release
                                pendingUpdate = null
                                startDownload(toDownload)
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
                        AppNavGraph(
                            navController = navController,
                            isDarkMode = isDarkMode,
                            onToggleTheme = { dark ->
                                isDarkMode = dark
                                scope.launch { prefs.setDarkMode(dark) }
                            }
                        )

                        // UpdateScreen overlay — shown during download/install
                        if (activeDownload != null) {
                            UpdateScreen(
                                release = activeDownload!!,
                                progress = downloadProgress,
                                isDone = downloadDone,
                                error = downloadError,
                                onRetry = { startDownload(activeDownload!!) },
                                onDismiss = {
                                    activeDownload = null
                                    downloadProgress = -1f
                                    downloadDone = false
                                    downloadError = null
                                }
                            )
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
