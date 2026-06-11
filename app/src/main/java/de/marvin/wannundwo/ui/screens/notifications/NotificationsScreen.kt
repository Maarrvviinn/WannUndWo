package de.marvin.wannundwo.ui.screens.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import de.marvin.wannundwo.model.AppNotification
import de.marvin.wannundwo.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NotificationsViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val repo = NotificationRepository()
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications = _notifications.asStateFlow()
    private val userId get() = auth.currentUser?.uid ?: ""

    init {
        viewModelScope.launch {
            repo.observeNotifications(userId).collect { _notifications.value = it }
        }
    }

    fun markAllRead() {
        viewModelScope.launch { repo.markAllRead(userId) }
    }

    fun markRead(notificationId: String) {
        viewModelScope.launch { repo.markRead(userId, notificationId) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val vm: NotificationsViewModel = viewModel()
    val notifications by vm.notifications.collectAsState()

    // Auto-mark all as read when screen opens
    LaunchedEffect(Unit) { vm.markAllRead() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Benachrichtigungen") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { vm.markAllRead() }) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Alle gelesen")
                    }
                }
            )
        }
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Keine Benachrichtigungen", fontSize = 16.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notifications, key = { it.id }) { n ->
                    NotificationItem(
                        notification = n,
                        onClick = {
                            if (!n.read) vm.markRead(n.id)
                            if (n.abholungId.isNotBlank()) onNavigateToDetail(n.abholungId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(notification: AppNotification, onClick: () -> Unit) {
    val dateStr = remember(notification.createdAt) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(notification.createdAt.toDate())
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.read) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!notification.read) {
                Box(modifier = Modifier.size(8.dp).offset(y = 6.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary) {
                        Spacer(Modifier.size(8.dp))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(notification.message, fontWeight = if (!notification.read) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp)
                Text(dateStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}
