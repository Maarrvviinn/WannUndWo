package de.marvin.wannundwo.ui.screens.haushalt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import de.marvin.wannundwo.model.Haushalt
import de.marvin.wannundwo.model.HaushaltGroup
import de.marvin.wannundwo.model.User
import de.marvin.wannundwo.repository.HaushaltRepository
import de.marvin.wannundwo.ui.components.QrCodeImage
import kotlinx.coroutines.launch

@Composable
fun HaushaltTab(
    haushalt: Haushalt?,
    members: List<User>,
    currentUserId: String,
    onCreateMember: () -> Unit,
    onEditPermissions: (String) -> Unit
) {
    var showQrCode by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var editGroup by remember { mutableStateOf<HaushaltGroup?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var memberToDelete by remember { mutableStateOf<User?>(null) }
    val isAdmin = haushalt?.adminId == currentUserId

    val groupVm: GroupViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val haushaltRepo = remember { HaushaltRepository() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(haushalt?.name ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (isAdmin) {
                            IconButton(onClick = { showRenameDialog = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, "Umbenennen", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                    Text("${members.size} Mitglied${if (members.size != 1) "er" else ""}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showQrCode = true }) {
                            Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Einladen")
                        }
                        if (isAdmin) {
                            Button(onClick = onCreateMember) {
                                Icon(Icons.Default.PersonAdd, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mitglied")
                            }
                        }
                    }
                }
            }
        }

        // ── GRUPPEN ──────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Gruppen", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (isAdmin) {
                    TextButton(onClick = { showCreateGroup = true }) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Neue Gruppe")
                    }
                }
            }
        }

        if (haushalt?.groups.isNullOrEmpty()) {
            item {
                Text(
                    "Noch keine Gruppen — erstelle eine, um Mitglieder zusammenzufassen.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
        } else {
            items(haushalt!!.groups, key = { it.id }) { group ->
                GroupCard(
                    group = group,
                    members = members,
                    isAdmin = isAdmin,
                    onEdit = { editGroup = group },
                    onDelete = { groupVm.deleteGroup(haushalt.id, group.id) }
                )
            }
        }

        // ── MITGLIEDER ───────────────────────────────────────────────
        item {
            Text("Mitglieder", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
        }

        items(members, key = { it.id }) { member ->
            MemberCard(
                member = member,
                isAdmin = haushalt?.adminId == member.id,
                canEditPermissions = isAdmin && member.id != currentUserId,
                canDelete = isAdmin && member.id != currentUserId && member.id != haushalt?.adminId,
                onEditPermissions = { onEditPermissions(member.id) },
                onDelete = { memberToDelete = member }
            )
        }
    }

    // QR Code Dialog
    if (showQrCode && haushalt != null) {
        Dialog(onDismissRequest = { showQrCode = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Einladungscode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    QrCodeImage(content = haushalt.inviteCode, size = 220)
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            haushalt.inviteCode,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                            fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 6.sp, color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    TextButton(onClick = { showQrCode = false }) { Text("Schließen") }
                }
            }
        }
    }

    // Gruppe erstellen Dialog
    if (showCreateGroup && haushalt != null) {
        GroupDialog(
            title = "Neue Gruppe",
            initialName = "",
            initialMemberIds = emptySet(),
            members = members,
            onConfirm = { name, memberIds ->
                groupVm.createGroup(haushalt.id, name, memberIds)
                showCreateGroup = false
            },
            onDismiss = { showCreateGroup = false }
        )
    }

    // Gruppe bearbeiten Dialog
    editGroup?.let { group ->
        if (haushalt != null) {
            GroupDialog(
                title = "Gruppe bearbeiten",
                initialName = group.name,
                initialMemberIds = group.memberIds.toSet(),
                members = members,
                onConfirm = { newName, memberIds ->
                    groupVm.updateGroup(haushalt.id, group.id, newName, memberIds.toList())
                    editGroup = null
                },
                onDismiss = { editGroup = null }
            )
        }
    }

    // Haushalt umbenennen Dialog
    if (showRenameDialog && haushalt != null) {
        var newName by remember { mutableStateOf(haushalt.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Haushalt umbenennen") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            scope.launch { haushaltRepo.updateHaushaltName(haushalt.id, newName.trim()) }
                            showRenameDialog = false
                        }
                    },
                    enabled = newName.isNotBlank()
                ) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Abbrechen") } }
        )
    }

    // Mitglied entfernen Dialog
    memberToDelete?.let { member ->
        if (haushalt != null) {
            AlertDialog(
                onDismissRequest = { memberToDelete = null },
                title = { Text("Mitglied entfernen?") },
                text = { Text("${member.name} wird aus dem Haushalt entfernt.") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch { haushaltRepo.removeMemberFromHaushalt(haushalt.id, member.id) }
                        memberToDelete = null
                    }) { Text("Entfernen", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { memberToDelete = null }) { Text("Abbrechen") } }
            )
        }
    }
}

@Composable
private fun GroupCard(
    group: HaushaltGroup,
    members: List<User>,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val groupMembers = members.filter { it.id in group.memberIds }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(group.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (isAdmin) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (groupMembers.isEmpty()) {
                Text("Keine Mitglieder", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            } else {
                Text(groupMembers.joinToString(", ") { it.name }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Gruppe löschen?") },
            text = { Text("\"${group.name}\" wird unwiderruflich gelöscht.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun GroupDialog(
    title: String,
    initialName: String,
    initialMemberIds: Set<String>,
    members: List<User>,
    onConfirm: (name: String, memberIds: Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selected by remember { mutableStateOf(initialMemberIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Gruppenname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (members.isNotEmpty()) {
                    Text("Mitglieder:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    members.forEach { member ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = member.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + member.id else selected - member.id
                                }
                            )
                            Text(member.name, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, selected) },
                enabled = name.isNotBlank()
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun MemberCard(member: User, isAdmin: Boolean, canEditPermissions: Boolean, canDelete: Boolean, onEditPermissions: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(member.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary, fontSize = 18.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (isAdmin) {
                        Icon(Icons.Default.AdminPanelSettings, "Admin", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                Text(member.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            if (canEditPermissions) {
                IconButton(onClick = onEditPermissions) {
                    Icon(Icons.Default.Settings, "Berechtigungen")
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.PersonRemove, "Entfernen", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
