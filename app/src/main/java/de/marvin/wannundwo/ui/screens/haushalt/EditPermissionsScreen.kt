package de.marvin.wannundwo.ui.screens.haushalt

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.marvin.wannundwo.model.Permissions
import de.marvin.wannundwo.model.User
import de.marvin.wannundwo.repository.HaushaltRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditPermissionsViewModel : ViewModel() {
    private val repo = HaushaltRepository()
    private val _user = MutableStateFlow<User?>(null)
    val user = _user.asStateFlow()
    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    fun load(userId: String) {
        viewModelScope.launch { _user.value = repo.getUser(userId) }
    }

    fun save(userId: String, perms: Permissions) {
        viewModelScope.launch {
            repo.updatePermissions(userId, perms)
            _saved.value = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPermissionsScreen(userId: String, onBack: () -> Unit) {
    val vm: EditPermissionsViewModel = viewModel()
    val user by vm.user.collectAsState()
    val saved by vm.saved.collectAsState()

    LaunchedEffect(userId) { vm.load(userId) }
    LaunchedEffect(saved) { if (saved) onBack() }

    var canCreate by remember { mutableStateOf(true) }

    LaunchedEffect(user) {
        user?.permissions?.let {
            canCreate = it.canCreateAbholung
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Berechtigungen: ${user?.name ?: ""}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Was darf ${user?.name ?: "dieses Mitglied"} tun?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            PermissionToggle("Abholungen erstellen", canCreate) { canCreate = it }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { vm.save(userId, Permissions(canCreateAbholung = canCreate)) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Speichern", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun PermissionToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
