package de.marvin.wannundwo.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val release: ReleaseInfo, val progress: Float) : DownloadState()
    data class Installing(val release: ReleaseInfo) : DownloadState()
    data class Error(val release: ReleaseInfo, val message: String) : DownloadState()
}

class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nm by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        private const val CHANNEL_ID = "apk_download"
        private const val NOTIF_ID = 9001

        private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val state = _state.asStateFlow()

        fun start(context: Context, release: ReleaseInfo) {
            _state.value = DownloadState.Downloading(release, -1f)
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra("version_code", release.versionCode)
                putExtra("version_name", release.versionName)
                putExtra("tag_name", release.tagName)
                putExtra("apk_url", release.apkUrl)
                putExtra("asset_name", release.assetName)
                putExtra("published_at", release.publishedAt)
            }
            context.startForegroundService(intent)
        }

        fun reset() {
            _state.value = DownloadState.Idle
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }

        val release = ReleaseInfo(
            versionCode = intent.getIntExtra("version_code", 0),
            versionName = intent.getStringExtra("version_name") ?: "",
            tagName = intent.getStringExtra("tag_name") ?: "",
            apkUrl = intent.getStringExtra("apk_url") ?: "",
            assetName = intent.getStringExtra("asset_name") ?: "",
            publishedAt = intent.getStringExtra("published_at") ?: ""
        )

        createChannel()
        startForeground(NOTIF_ID, buildProgressNotification(release.tagName, -1f))

        scope.launch {
            val file = GitHubUpdateManager.downloadApk(this@DownloadService, release) { p ->
                _state.value = DownloadState.Downloading(release, p)
                nm.notify(NOTIF_ID, buildProgressNotification(release.tagName, p))
            }
            if (file != null) {
                _state.value = DownloadState.Installing(release)
                nm.notify(NOTIF_ID, buildDoneNotification(release.tagName))
                ApkInstaller.install(this@DownloadService, file)
                // Give the system installer ~2s to launch, then clear the overlay.
                // Without this the UpdateScreen stays visible when the user returns
                // from the installer and they get prompted again.
                delay(2000)
                _state.value = DownloadState.Idle
            } else {
                _state.value = DownloadState.Error(release, "Download fehlgeschlagen")
                nm.cancel(NOTIF_ID)
            }
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App-Update",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Download der neuen App-Version" }
        nm.createNotificationChannel(channel)
    }

    private fun buildProgressNotification(tagName: String, progress: Float) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("WannUndWo $tagName wird geladen")
            .setContentText(
                if (progress < 0f) "Verbinde…" else "${(progress * 100).toInt()} %"
            )
            .setProgress(
                100,
                if (progress < 0f) 0 else (progress * 100).toInt(),
                progress < 0f
            )
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun buildDoneNotification(tagName: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("WannUndWo $tagName bereit")
            .setContentText("Installation wird gestartet…")
            .setOngoing(false)
            .setSilent(true)
            .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
