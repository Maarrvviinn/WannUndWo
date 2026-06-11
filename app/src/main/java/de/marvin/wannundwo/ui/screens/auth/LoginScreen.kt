package de.marvin.wannundwo.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val vm: LoginViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()

    var tab by remember { mutableIntStateOf(0) }

    // Login fields
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }

    // Register fields
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirm by remember { mutableStateOf("") }
    var showRegPassword by remember { mutableStateOf(false) }
    var regError by remember { mutableStateOf<String?>(null) }

    // Reset password dialog
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.success) { if (uiState.success) onLoginSuccess() }

    if (uiState.resetSent) {
        AlertDialog(
            onDismissRequest = { vm.clearResetSent() },
            title = { Text("E-Mail gesendet") },
            text = { Text("Eine E-Mail zum Zurücksetzen des Passworts wurde gesendet.") },
            confirmButton = { TextButton(onClick = { vm.clearResetSent() }) { Text("OK") } }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Passwort zurücksetzen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gib deine E-Mail-Adresse ein.")
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("E-Mail") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.sendPasswordReset(resetEmail); showResetDialog = false },
                    enabled = resetEmail.isNotBlank()
                ) { Text("Senden") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Abbrechen") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Wann & Wo",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (tab == 0) "Anmelden" else "Konto erstellen",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))

        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0; vm.clearError() }, text = { Text("Anmelden") })
            Tab(selected = tab == 1, onClick = { tab = 1; vm.clearError() }, text = { Text("Registrieren") })
        }

        Spacer(Modifier.height(20.dp))

        if (tab == 0) {
            // ── LOGIN ──────────────────────────────────────────────────
            OutlinedTextField(
                value = loginEmail,
                onValueChange = { loginEmail = it },
                label = { Text("E-Mail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = loginPassword,
                onValueChange = { loginPassword = it },
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    if (loginEmail.isNotBlank() && loginPassword.isNotBlank()) vm.login(loginEmail, loginPassword)
                }),
                trailingIcon = {
                    IconButton(onClick = { showLoginPassword = !showLoginPassword }) {
                        Icon(if (showLoginPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                }
            )

            (uiState.error)?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.login(loginEmail, loginPassword) },
                enabled = loginEmail.isNotBlank() && loginPassword.isNotBlank() && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (uiState.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Anmelden", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { resetEmail = loginEmail; showResetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Passwort vergessen?")
            }

        } else {
            // ── REGISTER ───────────────────────────────────────────────
            OutlinedTextField(
                value = regEmail,
                onValueChange = { regEmail = it },
                label = { Text("E-Mail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = regPassword,
                onValueChange = { regPassword = it },
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = if (showRegPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                trailingIcon = {
                    IconButton(onClick = { showRegPassword = !showRegPassword }) {
                        Icon(if (showRegPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                supportingText = { Text("Mindestens 6 Zeichen") }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = regConfirm,
                onValueChange = { regConfirm = it },
                label = { Text("Passwort bestätigen") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                isError = regConfirm.isNotBlank() && regConfirm != regPassword
            )

            val combinedError = regError ?: uiState.error
            combinedError?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    regError = null
                    when {
                        regEmail.isBlank() -> regError = "Bitte E-Mail eingeben"
                        regPassword.length < 6 -> regError = "Passwort muss mind. 6 Zeichen haben"
                        regPassword != regConfirm -> regError = "Passwörter stimmen nicht überein"
                        else -> vm.register(regEmail, regPassword)
                    }
                },
                enabled = regEmail.isNotBlank() && regPassword.isNotBlank() && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (uiState.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Registrieren", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

