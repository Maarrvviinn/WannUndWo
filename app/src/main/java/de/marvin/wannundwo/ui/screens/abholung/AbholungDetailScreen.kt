package de.marvin.wannundwo.ui.screens.abholung

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.marvin.wannundwo.model.AbholungChange
import de.marvin.wannundwo.model.AbholungStatus
import de.marvin.wannundwo.model.User
import de.marvin.wannundwo.ui.theme.StatusAbgelehnt
import de.marvin.wannundwo.ui.theme.StatusAngenommen
import de.marvin.wannundwo.ui.theme.StatusAusstehend
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbholungDetailScreen(
    abholungId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val vm: AbholungDetailViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(abholungId) { vm.load(abholungId) }
    // No auto-navigate on respond — stay to see live status updates

    var responseNote by remember { mutableStateOf("") }
    var showRespondSheet by remember { mutableStateOf<AbholungStatus?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var reminderNote by remember { mutableStateOf("") }

    val abholung = uiState.abholung
    val isCreator = abholung?.creatorId == vm.currentUserId
    val isRecipient = abholung?.recipientIds?.contains(vm.currentUserId) == true
    val isPending = abholung?.status == AbholungStatus.AUSSTEHEND.name
    val isActive = isPending || abholung?.status == AbholungStatus.ANGENOMMEN.name

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Abholung") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    if (isCreator && isActive) {
                        IconButton(onClick = { showReminderDialog = true }) {
                            Icon(Icons.Default.Alarm, "Erinnerung")
                        }
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, "Bearbeiten")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (abholung == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        val statusColor = when (abholung.status) {
            AbholungStatus.ANGENOMMEN.name -> StatusAngenommen
            AbholungStatus.ABGELEHNT.name -> StatusAbgelehnt
            else -> StatusAusstehend
        }
        val statusText = when (abholung.status) {
            AbholungStatus.ANGENOMMEN.name -> "Angenommen"
            AbholungStatus.ABGELEHNT.name -> "Abgelehnt"
            else -> "Ausstehend"
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = statusColor.copy(alpha = 0.15f)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(statusText, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }

            // Uhrzeit & Datum
            val dateStr = SimpleDateFormat("EEEE, dd. MMMM yyyy 'um' HH:mm 'Uhr'", Locale.GERMAN).format(abholung.time.toDate())
            InfoRow(icon = { Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary) }, text = dateStr)

            // Ort
            if (abholung.location.isNotBlank()) {
                InfoRow(icon = { Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.secondary) }, text = abholung.location)
            }

            // Notiz (vor creator/empfaenger)
            if (abholung.note.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(abholung.note, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
                }
            }

            // Reaktion (angenommen/abgelehnt) — nur responseNote anzeigen, kein Banner
            if (abholung.status != AbholungStatus.AUSSTEHEND.name && abholung.responseNote.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text("\"${abholung.responseNote}\"", modifier = Modifier.padding(12.dp), fontSize = 14.sp)
                }
            }

            // Erstellt von + Empfaenger (ausgegraut, unten)
            val dimAlpha = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            uiState.creator?.let { creator ->
                Text("Erstellt von: ${creator.name}", fontSize = 13.sp, color = dimAlpha)
            }
            if (uiState.recipients.isNotEmpty()) {
                Text("Darf abholen: ${uiState.recipients.joinToString(", ") { it.name }}", fontSize = 13.sp, color = dimAlpha)
            }

            // Annehmen / Ablehnen — auch wenn schon beantwortet (Meinung ändern)
            if (isRecipient && (isPending || abholung.status == AbholungStatus.ANGENOMMEN.name || abholung.status == AbholungStatus.ABGELEHNT.name)) {
                val alreadyResponded = !isPending
                Spacer(Modifier.height(4.dp))
                if (alreadyResponded) {
                    Text("Meinung ändern:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { showRespondSheet = AbholungStatus.ABGELEHNT },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAbgelehnt),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Ablehnen", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Button(
                        onClick = { showRespondSheet = AbholungStatus.ANGENOMMEN },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAngenommen),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Annehmen", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // Aenderungsverlauf
            if (uiState.changes.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                    Text("Änderungsverlauf", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                uiState.changes.forEach { change -> ChangeEntry(change, uiState.members) }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // Dialog: Reagieren
    showRespondSheet?.let { status ->
        AlertDialog(
            onDismissRequest = { showRespondSheet = null; responseNote = "" },
            title = { Text(if (status == AbholungStatus.ANGENOMMEN) "Abholung annehmen" else "Abholung ablehnen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Möchtest du eine Notiz hinzufügen?")
                    OutlinedTextField(value = responseNote, onValueChange = { responseNote = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.respond(abholungId, status, responseNote); showRespondSheet = null; responseNote = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = if (status == AbholungStatus.ANGENOMMEN) StatusAngenommen else StatusAbgelehnt)
                ) { Text(if (status == AbholungStatus.ANGENOMMEN) "Annehmen" else "Ablehnen", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showRespondSheet = null; responseNote = "" }) { Text("Abbrechen") } }
        )
    }

    // Dialog: Erinnerung
    if (showReminderDialog) {
        AlertDialog(
            onDismissRequest = { showReminderDialog = false; reminderNote = "" },
            title = { Text("Erinnerung senden") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Schickt eine Push-Benachrichtigung an alle, die dich abholen sollen.")
                    OutlinedTextField(value = reminderNote, onValueChange = { reminderNote = it }, label = { Text("Zusatz (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = { vm.sendReminder(reminderNote); showReminderDialog = false; reminderNote = "" }) {
                    Icon(Icons.Default.Alarm, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Erinnern")
                }
            },
            dismissButton = { TextButton(onClick = { showReminderDialog = false; reminderNote = "" }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun ChangeEntry(change: AbholungChange, members: List<User>) {
    val dateStr = remember(change.changedAt) { SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN).format(change.changedAt.toDate()) }

    val changedByName = members.find { it.id == change.changedBy }?.name ?: "Jemand"

    val description = when (change.field) {
        "note" -> "$changedByName hat die Notiz einer Abholung geändert."
        "time" -> {
            val old = change.oldValue; val new = change.newValue
            when {
                old.contains(":") && new.contains(":") -> "$changedByName hat die Uhrzeit einer Abholung geändert."
                else -> "$changedByName hat das Datum einer Abholung geändert."
            }
        }
        "location" -> "$changedByName hat den Ort einer Abholung geändert."
        "status" -> {
            when (change.newValue) {
                "ANGENOMMEN" -> "$changedByName hat die Abholung angenommen."
                "ABGELEHNT" -> "$changedByName hat die Abholung abgelehnt."
                else -> "$changedByName hat den Status geändert."
            }
        }
        else -> "$changedByName hat eine Abholung geändert."
    }

    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
            Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun InfoRow(icon: @Composable () -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        icon()
        Text(text, fontSize = 15.sp)
    }
}
