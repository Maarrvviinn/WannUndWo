package de.marvin.wannundwo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import de.marvin.wannundwo.data.UserPreferences
import de.marvin.wannundwo.navigation.AppNavGraph
import de.marvin.wannundwo.update.ApkInstaller
import de.marvin.wannundwo.update.GitHubUpdateManager
import de.marvin.wannundwo.update.ReleaseInfo
import de.marvin.wannundwo.ui.theme.WannUndWoTheme
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
            var updateError by remember { mutableStateOf<String?>(null) }
            var installing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                pendingUpdate = GitHubUpdateManager.checkForUpdate(context, BuildConfig.VERSION_CODE)
            }

            WannUndWoTheme(darkTheme = isDarkMode) {
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
                            Button(
                                enabled = !installing,
                                onClick = {
                                    scope.launch {
                                        installing = true
                                        val apkFile = GitHubUpdateManager.downloadApk(context, release)
                                        if (apkFile != null) {
                                            ApkInstaller.install(context, apkFile)
                                            pendingUpdate = null
                                        } else {
                                            updateError = "Download der neuen APK fehlgeschlagen."
                                        }
                                        installing = false
                                    }
                                }
                            ) {
                                androidx.compose.material3.Text(if (installing) "Lade..." else "Installieren")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { pendingUpdate = null }) {
                                androidx.compose.material3.Text("Später")
                            }
                        }
                    )
                }

                updateError?.let { message ->
                    AlertDialog(
                        onDismissRequest = { updateError = null },
                        title = { androidx.compose.material3.Text("Update-Fehler") },
                        text = { androidx.compose.material3.Text(message) },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = { updateError = null }) {
                                androidx.compose.material3.Text("OK")
                            }
                        }
                    )
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    AppNavGraph(
                        navController = navController,
                        isDarkMode = isDarkMode,
                        onToggleTheme = { dark ->
                            isDarkMode = dark
                            scope.launch { prefs.setDarkMode(dark) }
                        }
                    )
                }
            }
        }
    }
}
