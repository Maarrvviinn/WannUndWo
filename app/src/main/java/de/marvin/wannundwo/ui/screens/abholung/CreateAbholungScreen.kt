package de.marvin.wannundwo.ui.screens.abholung

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateAbholungScreen(
    abholungId: String?,
    onBack: () -> Unit
) {
    val vm: CreateAbholungViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(Unit) { vm.init(abholungId) }
    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onBack() }

    var selectedDate by remember { mutableStateOf(Date()) }
    var location by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    // selectedDrivers = Personen, die mich abholen dürfen / benachrichtigt werden
    var selectedDrivers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Beim Bearbeiten vorhandene Werte laden
    LaunchedEffect(uiState.existingAbholung) {
        uiState.existingAbholung?.let {
            selectedDate = it.time.toDate()
            location = it.location
            note = it.note
            selectedDrivers = it.recipientIds.toSet()
        }
    }
    // Standard: alle anderen Mitglieder sind berechtigt abzuholen
    LaunchedEffect(uiState.members) {
        if (uiState.existingAbholung == null && selectedDrivers.isEmpty() && uiState.members.isNotEmpty()) {
            selectedDrivers = uiState.members.map { it.id }.toSet()
        }
    }

    val dateStr = remember(selectedDate) { SimpleDateFormat("EEEE, dd. MMMM yyyy", Locale.GERMAN).format(selectedDate) }
    val timeStr = remember(selectedDate) { SimpleDateFormat("HH:mm", Locale.GERMAN).format(selectedDate) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (abholungId != null) "Abholung bearbeiten" else "Abholung anfragen") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.save(selectedDate, location, null, null, note, selectedDrivers.toList()) },
                icon = {
                    if (uiState.isLoading) CircularProgressIndicator(Modifier.size(20.dp))
                    else Icon(Icons.Default.Check, null)
                },
                text = { Text("Speichern", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── WANN? ─────────────────────────────────────────────────
            SectionLabel("Wann?")

            // Datum
            OutlinedCard(modifier = Modifier.fillMaxWidth(), onClick = { showDatePicker = true }) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Datum", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text(dateStr, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Default.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }

            // Uhrzeit + Zuletzt-Chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(modifier = Modifier.fillMaxWidth(), onClick = { showTimePicker = true }) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.secondary)
                        Column(Modifier.weight(1f)) {
                            Text("Uhrzeit", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(timeStr, style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        Icon(Icons.Default.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                }
                if (uiState.recentTimes.isNotEmpty()) {
                    RecentRow(items = uiState.recentTimes) { t ->
                        val parts = t.split(":")
                        if (parts.size == 2) {
                            val cal = Calendar.getInstance().apply { time = selectedDate }
                            cal.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 0)
                            cal.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
                            selectedDate = cal.time
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // ── WO? ───────────────────────────────────────────────────
            SectionLabel("Wo?")

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Ort / Adresse") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                    trailingIcon = if (location.isNotBlank()) {
                        { IconButton(onClick = { location = "" }) { Icon(Icons.Default.Clear, null) } }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (uiState.recentLocations.isNotEmpty()) {
                    RecentRow(items = uiState.recentLocations) { location = it }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // ── NOTIZ ─────────────────────────────────────────────────
            SectionLabel("Notiz")

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Optional") },
                leadingIcon = { Icon(Icons.Default.Notes, null) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 4
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // ── WER DARF MICH ABHOLEN? ────────────────────────────────
            if (uiState.members.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Wer darf mich abholen?")
                    Text(
                        "Nur diese Personen sehen deine Anfrage und können sie annehmen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )

                    // Gruppen-Schnellauswahl
                    if (uiState.groups.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.groups) { group ->
                                val allSelected = group.memberIds.isNotEmpty() &&
                                        group.memberIds.all { it in selectedDrivers }
                                FilterChip(
                                    selected = allSelected,
                                    onClick = {
                                        selectedDrivers = if (allSelected)
                                            selectedDrivers - group.memberIds.toSet()
                                        else
                                            selectedDrivers + group.memberIds.toSet()
                                    },
                                    label = { Text(group.name) },
                                    leadingIcon = {
                                        Icon(
                                            if (allSelected) Icons.Default.Group else Icons.Default.Group,
                                            null, Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Einzelne Mitglieder
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        uiState.members.forEach { member ->
                            val displayName = uiState.memberNames[member.id] ?: member.name
                            FilterChip(
                                selected = member.id in selectedDrivers,
                                onClick = {
                                    selectedDrivers = if (member.id in selectedDrivers)
                                        selectedDrivers - member.id
                                    else selectedDrivers + member.id
                                },
                                label = { Text(displayName) },
                                leadingIcon = if (member.id in selectedDrivers) {
                                    { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    // ── DATE PICKER ───────────────────────────────────────────────────
    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val cal = Calendar.getInstance().apply { timeInMillis = ms }
                        val cur = Calendar.getInstance().apply { time = selectedDate }
                        cur.set(Calendar.YEAR, cal.get(Calendar.YEAR))
                        cur.set(Calendar.MONTH, cal.get(Calendar.MONTH))
                        cur.set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH))
                        selectedDate = cur.time
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                todayDateBorderColor = MaterialTheme.colorScheme.primary,
                todayContentColor = MaterialTheme.colorScheme.primary,
                selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
                currentYearContentColor = MaterialTheme.colorScheme.primary,
                dayContentColor = MaterialTheme.colorScheme.onSurface,
                weekdayContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                subheadContentColor = MaterialTheme.colorScheme.onSurface,
                headlineContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                navigationContentColor = MaterialTheme.colorScheme.onSurface,
                yearContentColor = MaterialTheme.colorScheme.onSurface,
            )
        ) {
            DatePicker(state = state)
        }
    }

    // ── TIME PICKER ───────────────────────────────────────────────────
    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { time = selectedDate }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val c = Calendar.getInstance().apply { time = selectedDate }
                    c.set(Calendar.HOUR_OF_DAY, state.hour)
                    c.set(Calendar.MINUTE, state.minute)
                    selectedDate = c.time
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") } },
            containerColor = MaterialTheme.colorScheme.surface,
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun RecentRow(items: List<String>, onSelect: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Zuletzt:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(items) { item ->
                SuggestionChip(
                    onClick = { onSelect(item) },
                    label = { Text(item, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.History, null, Modifier.size(14.dp)) }
                )
            }
        }
    }
}

