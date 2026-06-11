package de.marvin.wannundwo.ui.screens.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.marvin.wannundwo.data.UserPreferences
import de.marvin.wannundwo.repository.AuthRepository
import de.marvin.wannundwo.repository.HaushaltRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
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
                onSuccess = {
                    val user = haushaltRepo.getUser(it.uid)
                    prefs.saveAccount(
                        email = email.trim(),
                        displayName = user?.name?.ifBlank { email.trim() } ?: email.trim(),
                        password = password
                    )
                    _uiState.value = LoginUiState(success = true)
                },
                onFailure = { _uiState.value = LoginUiState(error = it.message ?: "Login fehlgeschlagen") }
            )
        }
    }
}
