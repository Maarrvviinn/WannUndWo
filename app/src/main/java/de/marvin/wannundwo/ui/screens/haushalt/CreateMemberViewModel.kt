package de.marvin.wannundwo.ui.screens.haushalt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import de.marvin.wannundwo.repository.HaushaltRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class CreateMemberUiState(
    val isLoading: Boolean = false,
    val isDone: Boolean = false,
    val error: String? = null
)

class CreateMemberViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val haushaltRepo = HaushaltRepository()

    private val _uiState = MutableStateFlow(CreateMemberUiState())
    val uiState = _uiState.asStateFlow()

    fun createMember(name: String, email: String, password: String) {
        viewModelScope.launch {
            if (name.isBlank() || email.isBlank() || password.isBlank()) {
                _uiState.value = CreateMemberUiState(error = "Alle Felder müssen ausgefüllt sein")
                return@launch
            }
            if (password.length < 6) {
                _uiState.value = CreateMemberUiState(error = "Passwort muss mindestens 6 Zeichen lang sein")
                return@launch
            }
            _uiState.value = CreateMemberUiState(isLoading = true)
            try {
                val currentUser = haushaltRepo.getUser(auth.currentUser?.uid ?: "")
                val haushaltsId = currentUser?.haushaltsId
                    ?: throw Exception("Kein Haushalt gefunden")

                // Secondary FirebaseApp: creates user without signing out current user
                val ctx = getApplication<Application>()
                val secondaryApp = try {
                    FirebaseApp.getInstance("secondary")
                } catch (e: IllegalStateException) {
                    FirebaseApp.initializeApp(ctx, FirebaseApp.getInstance().options, "secondary")
                } ?: throw Exception("FirebaseApp konnte nicht initialisiert werden")

                val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
                val result = secondaryAuth.createUserWithEmailAndPassword(email.trim(), password).await()
                val newUid = result.user!!.uid
                secondaryAuth.signOut()

                haushaltRepo.createUserDoc(newUid, name.trim(), email.trim(), haushaltsId)
                haushaltRepo.addMemberToHaushalt(haushaltsId, newUid, name.trim())
                _uiState.value = CreateMemberUiState(isDone = true)
            } catch (e: Exception) {
                _uiState.value = CreateMemberUiState(error = e.message ?: "Fehler beim Erstellen des Accounts")
            }
        }
    }
}
