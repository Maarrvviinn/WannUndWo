package de.marvin.wannundwo.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AbholungCard(
    abholung: Abholung,
    members: List<User>,
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
    val dateStr = remember(abholung.time) {
        SimpleDateFormat("EEE, dd.MM.yyyy HH:mm", Locale.GERMAN).format(abholung.time.toDate())
    }

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
                    Text(dateStr, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
}
