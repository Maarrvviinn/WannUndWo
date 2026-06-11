package de.marvin.wannundwo.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.marvin.wannundwo.ui.components.QrScannerSheet

@Composable
fun HaushaltSetupScreen(
    userEmail: String,
    onSetupComplete: () -> Unit
) {
    val vm: HaushaltSetupViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var haushaltsName by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.navigateToHome) {
        if (uiState.navigateToHome) onSetupComplete()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Wann & Wo", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Haushalt einrichten", fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = userName,
            onValueChange = { userName = it },
            label = { Text("Dein Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Neu erstellen") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Beitreten") })
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (tab) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = haushaltsName,
                    onValueChange = { haushaltsName = it },
                    label = { Text("Name des Haushalts") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { vm.createHaushalt(haushaltsName, userName, userEmail) },
                    enabled = haushaltsName.isNotBlank() && userName.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Haushalt erstellen", fontWeight = FontWeight.SemiBold)
                }
            }
            1 -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it.uppercase().take(6) },
                    label = { Text("Einladungscode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                )
                OutlinedButton(
                    onClick = { showQrScanner = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("QR-Code scannen")
                }
                Button(
                    onClick = { vm.joinHaushalt(joinCode, userName, userEmail) },
                    enabled = joinCode.length == 6 && userName.isNotBlank() && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Beitreten", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        uiState.error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
    }

    if (showQrScanner) {
        QrScannerSheet(
            onCodeScanned = { code ->
                joinCode = code.take(6)
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false }
        )
    }
}
