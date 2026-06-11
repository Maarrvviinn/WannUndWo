package de.marvin.wannundwo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import de.marvin.wannundwo.model.Abholung
import de.marvin.wannundwo.model.AbholungStatus
import de.marvin.wannundwo.model.User
import de.marvin.wannundwo.ui.theme.StatusAbgelehnt
import de.marvin.wannundwo.ui.theme.StatusAngenommen
import de.marvin.wannundwo.ui.theme.StatusAusstehend
import de.marvin.wannundwo.util.toRelativeLabel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AbholungCard(
    abholung: Abholung,
    members: List<User>,
    currentUserId: String = "",
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onRespond: ((AbholungStatus, String) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRespondDialog by remember { mutableStateOf<AbholungStatus?>(null) }
    var responseNote by remember { mutableStateOf("") }
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
    val timeLabel = remember(abholung.time) { abholung.time.toRelativeLabel() }
    val isRecipient = currentUserId.isNotBlank() && currentUserId in abholung.recipientIds
    val isPending = abholung.status == AbholungStatus.AUSSTEHEND.name

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
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
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(timeLabel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Surface(shape = RoundedCornerShape(20.dp), color = statusColor.copy(alpha = 0.2f)) {
                    Text(statusText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (abholung.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Text(abholung.location, fontSize = 14.sp, maxLines = 1)
                }
            }

            if (abholung.note.isNotBlank()) {
                Text(abholung.note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 2)
            }

            // Inline respond buttons for list view
            if (isRecipient && isPending && onRespond != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showRespondDialog = AbholungStatus.ABGELEHNT },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAbgelehnt)
                    ) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ablehnen", fontSize = 13.sp)
                    }
                    Button(
                        onClick = { showRespondDialog = AbholungStatus.ANGENOMMEN },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAngenommen)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Annehmen", fontSize = 13.sp, color = Color.White)
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

    // Respond dialog (from inline buttons)
    showRespondDialog?.let { targetStatus ->
        AlertDialog(
            onDismissRequest = { showRespondDialog = null; responseNote = "" },
            title = { Text("Abholung beantworten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notiz hinzufügen? (optional)")
                    OutlinedTextField(
                        value = responseNote,
                        onValueChange = { responseNote = it },
                        label = { Text("Notiz") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onRespond?.invoke(targetStatus, responseNote); showRespondDialog = null; responseNote = "" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (targetStatus == AbholungStatus.ANGENOMMEN) StatusAngenommen else StatusAbgelehnt
                    )
                ) {
                    Text(if (targetStatus == AbholungStatus.ANGENOMMEN) "Annehmen" else "Ablehnen", color = Color.White)
                }
            },
            dismissButton = { TextButton(onClick = { showRespondDialog = null; responseNote = "" }) { Text("Abbrechen") } }
        )
    }
}
