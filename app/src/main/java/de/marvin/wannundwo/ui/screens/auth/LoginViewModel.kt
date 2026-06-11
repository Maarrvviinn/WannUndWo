package de.marvin.wannundwo.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import de.marvin.wannundwo.data.UserPreferences
import de.marvin.wannundwo.repository.AuthRepository
import de.marvin.wannundwo.repository.HaushaltRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val resetSent: Boolean = false
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepo = AuthRepository()
    private val haushaltRepo = HaushaltRepository()
    private val prefs = UserPreferences(application)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState(isLoading = true)
            val result = authRepo.login(email.trim(), password)
            result.fold(
                onSuccess = { user ->
                    val userDoc = haushaltRepo.getUser(user.uid)
                    prefs.saveAccount(
                        email = email.trim(),
                        displayName = userDoc?.name?.ifBlank { email.trim() } ?: email.trim(),
                        password = password
                    )
                    // Save FCM token — onNewToken fires before login so the token
                    // was never persisted. Always save it on successful login.
                    try {
                        val token = FirebaseMessaging.getInstance().token.await()
                        haushaltRepo.updateUserFcmToken(user.uid, token)
                    } catch (_: Exception) { /* non-fatal */ }
                    _uiState.value = LoginUiState(success = true)
                },
                onFailure = { _uiState.value = LoginUiState(error = it.message ?: "Login fehlgeschlagen") }
            )
        }
    }

    fun register(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState(isLoading = true)
            authRepo.register(email.trim(), password).fold(
                onSuccess = { user ->
                    // Save FCM token immediately after account creation
                    try {
                        val token = FirebaseMessaging.getInstance().token.await()
                        haushaltRepo.updateUserFcmToken(user.uid, token)
                    } catch (_: Exception) { /* non-fatal */ }
                    _uiState.value = LoginUiState(success = true)
                },
                onFailure = { _uiState.value = LoginUiState(error = it.message ?: "Registrierung fehlgeschlagen") }
            )
        }
    }

    fun sendPasswordReset(email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            authRepo.sendPasswordReset(email.trim()).fold(
                onSuccess = { _uiState.value = _uiState.value.copy(isLoading = false, resetSent = true) },
                onFailure = { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message ?: "Fehler beim Senden") }
            )
        }
    }

    fun clearResetSent() {
        _uiState.value = _uiState.value.copy(resetSent = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
