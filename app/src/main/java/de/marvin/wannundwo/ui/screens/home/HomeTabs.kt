package de.marvin.wannundwo.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.marvin.wannundwo.model.Abholung
import de.marvin.wannundwo.model.AbholungStatus
import de.marvin.wannundwo.model.User
import de.marvin.wannundwo.ui.components.AbholungCard
import de.marvin.wannundwo.ui.theme.StatusAbgelehnt
import de.marvin.wannundwo.ui.theme.StatusAngenommen
import de.marvin.wannundwo.ui.theme.StatusAusstehend
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun AnstehendeAbholungenTab(
    abholungen: List<Abholung>,
    members: List<User>,
    currentUser: User?,
    onDetail: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRespond: (abholungId: String, status: AbholungStatus, note: String) -> Unit
) {
    if (abholungen.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Keine Abholungen vorhanden", fontSize = 16.sp, textAlign = TextAlign.Center)
        }
        return
    }

    if (abholungen.size == 1) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
            val a = abholungen.first()
            val canEdit = a.creatorId == currentUser?.id
            val canDelete = a.creatorId == currentUser?.id
            BigAbholungCard(
                abholung = a,
                members = members,
                currentUserId = currentUser?.id ?: "",
                onClick = { onDetail(a.id) },
                onEdit = if (canEdit) { { onEdit(a.id) } } else null,
                onDelete = if (canDelete) { { onDelete(a.id) } } else null,
                onRespond = { status, note -> onRespond(a.id, status, note) }
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(abholungen, key = { it.id }) { abholung ->
            val canEdit = abholung.creatorId == currentUser?.id
            val canDelete = abholung.creatorId == currentUser?.id
            AbholungCard(
                abholung = abholung,
                members = members,
                onClick = { onDetail(abholung.id) },
                onEdit = if (canEdit) { { onEdit(abholung.id) } } else null,
                onDelete = if (canDelete) { { onDelete(abholung.id) } } else null
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BigAbholungCard(
    abholung: Abholung,
    members: List<User>,
    currentUserId: String,
    onRespond: ((AbholungStatus, String) -> Unit)?,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
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
    val timeStr = remember(abholung.time) { SimpleDateFormat("HH:mm", Locale.GERMAN).format(abholung.time.toDate()) }
    val dateStr = remember(abholung.time) { SimpleDateFormat("EEEE, dd. MMMM", Locale.GERMAN).format(abholung.time.toDate()) }
    val isRecipient = currentUserId in abholung.recipientIds
    val isPending = abholung.status == AbholungStatus.AUSSTEHEND.name

    // Countdown
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); nowMs = System.currentTimeMillis() } }
    val diffSeconds = (abholung.time.toDate().time - nowMs) / 1000
    val isOverdue = diffSeconds < 0
    val isExpired = diffSeconds < -600
    val countdownColor: Color = if (isExpired) StatusAbgelehnt else StatusAngenommen
    val countdownText = run {
        val s = abs(diffSeconds)
        val base = if (s >= 3600) "${s / 3600}h ${(s % 3600) / 60}min" else "${(s % 3600) / 60}min ${s % 60}s"
        if (isOverdue) "vor $base" else "in $base"
    }

    // Respond state
    var showRespondDialog by remember { mutableStateOf(false) }
    var responseNote by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { offset ->
                    if (onEdit != null || onDelete != null) {
                        menuOffset = DpOffset(offset.x.toDp(), offset.y.toDp())
                        showMenu = true
                    }
                }
            )
        },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // Kopfzeile
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(statusText, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Uhrzeit
            Text(timeStr, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)

            // Datum
            Text(dateStr, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

            // Countdown
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, tint = countdownColor, modifier = Modifier.size(16.dp))
                Text(countdownText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = countdownColor)
            }

            // Ort
            if (abholung.location.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Text(abholung.location, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }

            // Notiz
            if (abholung.note.isNotBlank()) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(abholung.note, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── Annehmen / Ablehnen Buttons ───────────────────────────
            if (isRecipient && isPending && onRespond != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { showRespondDialog = true },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAbgelehnt),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Ablehnen", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }
                    Button(
                        onClick = { showRespondDialog = true },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAngenommen),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Annehmen", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }
                }
            }
        }
    }

    // Long-press Dropdown
    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, offset = menuOffset) {
        onEdit?.let {
            DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { showMenu = false; it() })
        }
        onDelete?.let {
            DropdownMenuItem(
                text = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
                onClick = { showMenu = false; showDeleteDialog = true }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Abholung löschen?") },
            text = { Text("Diese Abholung wird unwiderruflich gelöscht.") },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteDialog = false }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") } }
        )
    }

    // Dialog für optionale Notiz
    if (showRespondDialog && onRespond != null) {
        AlertDialog(
            onDismissRequest = { showRespondDialog = false; responseNote = "" },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Abholung beantworten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Möchtest du eine Notiz hinzufügen?")
                    OutlinedTextField(
                        value = responseNote,
                        onValueChange = { responseNote = it },
                        label = { Text("Notiz (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onRespond.invoke(AbholungStatus.ANGENOMMEN, responseNote); showRespondDialog = false; responseNote = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAngenommen)
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Annehmen", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { onRespond.invoke(AbholungStatus.ABGELEHNT, responseNote); showRespondDialog = false; responseNote = "" },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAbgelehnt)
                ) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Ablehnen", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        )
    }
}

@Composable
fun VerlaufTab(
    abholungen: List<Abholung>,
    members: List<User>,
    onDetail: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredAbholungen = remember(abholungen, searchQuery) {
        filterAbholungen(abholungen, searchQuery, members)
    }
    
    if (abholungen.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Kein Verlauf vorhanden", fontSize = 16.sp)
        }
        return
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Suche im Verlauf...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        if (filteredAbholungen.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Keine Einträge gefunden", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        } else {
            items(filteredAbholungen, key = { it.id }) { abholung ->
                AbholungCard(abholung = abholung, members = members, onClick = { onDetail(abholung.id) }, onEdit = null, onDelete = null)
            }
        }
    }
}

private fun filterAbholungen(abholungen: List<Abholung>, searchQuery: String, members: List<User>): List<Abholung> {
    return if (searchQuery.isBlank()) {
        abholungen
    } else {
        abholungen.filter { abholung ->
            abholung.location.contains(searchQuery, ignoreCase = true) ||
            abholung.note.contains(searchQuery, ignoreCase = true) ||
            members.find { it.id == abholung.creatorId }?.name?.contains(searchQuery, ignoreCase = true) == true
        }
    }
}
