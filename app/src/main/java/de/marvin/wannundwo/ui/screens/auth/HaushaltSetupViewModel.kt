package de.marvin.wannundwo.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.marvin.wannundwo.repository.AuthRepository
import de.marvin.wannundwo.repository.HaushaltRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HaushaltSetupUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToHome: Boolean = false
)

class HaushaltSetupViewModel : ViewModel() {
    private val authRepo = AuthRepository()
    private val haushaltRepo = HaushaltRepository()

    private val _uiState = MutableStateFlow(HaushaltSetupUiState())
    val uiState = _uiState.asStateFlow()

    fun createHaushalt(name: String, userName: String, userEmail: String) {
        viewModelScope.launch {
            if (name.isBlank()) { _uiState.value = _uiState.value.copy(error = "Name darf nicht leer sein"); return@launch }
            _uiState.value = HaushaltSetupUiState(isLoading = true)
            val userId = authRepo.currentUserId
            val result = haushaltRepo.createHaushalt(name.trim(), userId, userName.trim())
            result.fold(
                onSuccess = { haushalt ->
                    haushaltRepo.createUserDoc(userId, userName.trim(), userEmail.trim(), haushalt.id)
                    _uiState.value = HaushaltSetupUiState(navigateToHome = true)
                },
                onFailure = { _uiState.value = HaushaltSetupUiState(error = it.message) }
            )
        }
    }

    fun joinHaushalt(code: String, userName: String, userEmail: String) {
        viewModelScope.launch {
            if (code.isBlank()) { _uiState.value = _uiState.value.copy(error = "Bitte Code eingeben"); return@launch }
            _uiState.value = HaushaltSetupUiState(isLoading = true)
            val userId = authRepo.currentUserId
            val result = haushaltRepo.joinHaushalt(code.trim().uppercase(), userId, userName.trim())
            result.fold(
                onSuccess = { haushalt ->
                    haushaltRepo.createUserDoc(userId, userName.trim(), userEmail.trim(), haushalt.id)
                    _uiState.value = HaushaltSetupUiState(navigateToHome = true)
                },
                onFailure = { _uiState.value = HaushaltSetupUiState(error = it.message) }
            )
        }
    }
}
