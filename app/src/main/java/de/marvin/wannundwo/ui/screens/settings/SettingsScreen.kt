package de.marvin.wannundwo.ui.screens.settings

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import de.marvin.wannundwo.BuildConfig
import de.marvin.wannundwo.model.NotificationPreferences
import de.marvin.wannundwo.data.SavedAccount
import de.marvin.wannundwo.data.UserPreferences
import de.marvin.wannundwo.repository.HaushaltRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SettingsUiState(
    val savedAccounts: List<SavedAccount> = emptyList(),
    val memberNameInHaushalt: String = "",
    val isSwitching: Boolean = false,
    val error: String? = null,
    val switchDone: Boolean = false,
    val notificationPreferences: NotificationPreferences = NotificationPreferences()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val haushaltRepo = HaushaltRepository()
    private val prefs = UserPreferences(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private var haushaltsId = ""

    init {
        viewModelScope.launch {
            prefs.savedAccounts.collect { accounts ->
                _uiState.value = _uiState.value.copy(savedAccounts = accounts)
            }
        }
        viewModelScope.launch {
            val user = haushaltRepo.getUser(auth.currentUser?.uid ?: "") ?: return@launch
            haushaltsId = user.haushaltsId
            prefs.saveAccount(user.email, user.name.ifBlank { user.email })
            haushaltRepo.observeHaushalt(haushaltsId).collect { haushalt ->
                val name = haushalt?.memberNames?.get(auth.currentUser?.uid) ?: user.name
                _uiState.value = _uiState.value.copy(memberNameInHaushalt = name)
            }
        }
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            haushaltRepo.observeUser(uid).collect { user ->
                if (user != null) {
                    _uiState.value = _uiState.value.copy(notificationPreferences = user.notificationPreferences)
                }
            }
        }
    }

    fun saveCurrentAccount(displayName: String) {
        viewModelScope.launch {
            val email = auth.currentUser?.email ?: return@launch
            prefs.saveAccount(email, displayName)
        }
    }

    fun switchToAccount(account: SavedAccount, passwordOverride: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSwitching = true, error = null)
            try {
                val effectivePassword = passwordOverride ?: account.password
                if (effectivePassword.isBlank()) throw IllegalArgumentException("Passwort fehlt")
                auth.signInWithEmailAndPassword(account.email, effectivePassword).await()
                prefs.saveAccount(account.email, account.displayName, effectivePassword)
                _uiState.value = _uiState.value.copy(isSwitching = false, switchDone = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSwitching = false, error = e.message)
            }
        }
    }

    fun removeAccount(email: String) {
        viewModelScope.launch { prefs.removeAccount(email) }
    }

    fun updateNotificationPreference(key: String, enabled: Boolean) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            haushaltRepo.updateNotificationPreference(uid, key, enabled)
        }
    }

    fun updateNameInHaushalt(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val hId = haushaltsId.takeIf { it.isNotBlank() } ?: return@launch
            haushaltRepo.updateMemberNameInHaushalt(hId, uid, newName)
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onAddAccount: () -> Unit = {},
    onAccountSwitched: () -> Unit = {},
    onBack: () -> Unit
) {
    val vm: SettingsViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val currentEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
    val appVersionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSwitchDialog by remember { mutableStateOf(false) }
    var switchPassword by remember { mutableStateOf("") }
    var selectedAccountForSwitch by remember { mutableStateOf<SavedAccount?>(null) }
    var showSwitchPassword by remember { mutableStateOf(false) }
    var showAccountList by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.memberNameInHaushalt) {
        if (editingName == null) editingName = uiState.memberNameInHaushalt
    }
    LaunchedEffect(uiState.switchDone) {
        if (uiState.switchDone) onAccountSwitched()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } }
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── ACCOUNT INFO ──────────────────────────────────────────
            Text("Account", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(currentEmail, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showAccountList = !showAccountList }) {
                            Text(if (showAccountList) "Konten ausblenden" else "Konten anzeigen")
                        }
                        IconButton(onClick = { showAccountList = !showAccountList }) {
                            Icon(if (showAccountList) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }
                    }

                    if (showAccountList) {
                        val otherAccounts = uiState.savedAccounts.filter { it.email != currentEmail }
                        if (otherAccounts.isEmpty()) {
                            Text("Keine weiteren Konten gespeichert", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        } else {
                            otherAccounts.forEach { account ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text(account.displayName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text(account.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                                    }
                                    if (uiState.isSwitching) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        OutlinedButton(onClick = {
                                            if (account.password.isNotBlank()) {
                                                vm.switchToAccount(account)
                                            } else {
                                                selectedAccountForSwitch = account
                                                switchPassword = ""
                                                showSwitchPassword = false
                                                showSwitchDialog = true
                                            }
                                        }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp)) {
                                            Text("Wechseln", fontSize = 12.sp)
                                        }
                                    }
                                    IconButton(onClick = { vm.removeAccount(account.email) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                    }
                                }
                            }
                        }
                        TextButton(onClick = {
                            vm.saveCurrentAccount(uiState.memberNameInHaushalt.ifBlank { currentEmail })
                            onAddAccount()
                        }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Konto hinzufügen")
                        }
                    }

                    // Name im Haushalt
                    Text("Name im Haushalt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = editingName.orEmpty(),
                            onValueChange = { editingName = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Anzeigename") },
                            placeholder = { Text(uiState.memberNameInHaushalt) }
                        )
                        Button(onClick = {
                            val nameToSave = editingName.orEmpty().ifBlank { uiState.memberNameInHaushalt }
                            vm.updateNameInHaushalt(nameToSave)
                        }) {
                            Text("OK")
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            // ── DARSTELLUNG ───────────────────────────────────────────
            Text("Benachrichtigungen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NotificationToggleRow(
                        label = "Neue Abholung",
                        checked = uiState.notificationPreferences.abholungErstellt,
                        onChecked = { vm.updateNotificationPreference("abholungErstellt", it) }
                    )
                    NotificationToggleRow(
                        label = "Abholung geändert",
                        checked = uiState.notificationPreferences.abholungGeaendert,
                        onChecked = { vm.updateNotificationPreference("abholungGeaendert", it) }
                    )
                    NotificationToggleRow(
                        label = "Abholung angenommen",
                        checked = uiState.notificationPreferences.abholungAngenommen,
                        onChecked = { vm.updateNotificationPreference("abholungAngenommen", it) }
                    )
                    NotificationToggleRow(
                        label = "Abholung abgelehnt",
                        checked = uiState.notificationPreferences.abholungAbgelehnt,
                        onChecked = { vm.updateNotificationPreference("abholungAbgelehnt", it) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            // ── DARSTELLUNG ───────────────────────────────────────────
            Text("Darstellung", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(if (isDarkMode) "Dunkles Design" else "Helles Design", style = MaterialTheme.typography.bodyLarge)
                    }
                    Switch(checked = isDarkMode, onCheckedChange = onToggleTheme)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "App-Version: $appVersionLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Abmelden", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Abmelden?") },
            text = { Text("Möchtest du dich wirklich abmelden?") },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("Abmelden", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Abbrechen") } }
        )
    }

    if (showSwitchDialog && selectedAccountForSwitch != null) {
        AlertDialog(
            onDismissRequest = {
                showSwitchDialog = false
                switchPassword = ""
                showSwitchPassword = false
                selectedAccountForSwitch = null
            },
            title = { Text("Konto wechseln") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Passwort für ${selectedAccountForSwitch!!.email} eingeben")
                    OutlinedTextField(
                        value = switchPassword,
                        onValueChange = { switchPassword = it },
                        label = { Text("Passwort") },
                        singleLine = true,
                        visualTransformation = if (showSwitchPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showSwitchPassword = !showSwitchPassword }) {
                                Icon(
                                    if (showSwitchPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.switchToAccount(selectedAccountForSwitch!!, switchPassword)
                        showSwitchDialog = false
                        switchPassword = ""
                        showSwitchPassword = false
                        selectedAccountForSwitch = null
                    },
                    enabled = switchPassword.isNotBlank()
                ) {
                    Text("Wechseln")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSwitchDialog = false
                    switchPassword = ""
                    showSwitchPassword = false
                    selectedAccountForSwitch = null
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun NotificationToggleRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

