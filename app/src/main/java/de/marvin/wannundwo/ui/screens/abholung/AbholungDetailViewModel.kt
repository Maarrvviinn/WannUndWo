package de.marvin.wannundwo.ui.screens.abholung

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import de.marvin.wannundwo.model.Abholung
import de.marvin.wannundwo.model.AbholungChange
import de.marvin.wannundwo.model.AbholungStatus
import de.marvin.wannundwo.model.User
import de.marvin.wannundwo.repository.AbholungRepository
import de.marvin.wannundwo.repository.HaushaltRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class DetailUiState(
    val abholung: Abholung? = null,
    val creator: User? = null,
    val respondedByUser: User? = null,
    val recipients: List<User> = emptyList(),
    val members: List<User> = emptyList(),
    val changes: List<AbholungChange> = emptyList(),
    val isLoading: Boolean = false,
    val isDone: Boolean = false
)

class AbholungDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val auth = FirebaseAuth.getInstance()
    private val abholungRepo = AbholungRepository()
    private val haushaltRepo = HaushaltRepository()
    private val functions = FirebaseFunctions.getInstance()

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState = _uiState.asStateFlow()

    val currentUserId get() = auth.currentUser?.uid ?: ""

    fun load(abholungId: String) {
        viewModelScope.launch {
            abholungRepo.observeAbholung(abholungId)
                .filterNotNull()
                .flatMapLatest { a ->
                    val creator = haushaltRepo.getUser(a.creatorId)
                    val responded = if (a.respondedBy.isNotBlank()) haushaltRepo.getUser(a.respondedBy) else null
                    val memberIds = (a.recipientIds + a.creatorId).distinct()
                    haushaltRepo.observeMembers(memberIds).map { members ->
                        DetailUiState(
                            abholung = a,
                            creator = creator,
                            respondedByUser = responded,
                            recipients = members.filter { it.id in a.recipientIds },
                            members = members
                        )
                    }
                }
                .collect { state ->
                    _uiState.value = _uiState.value.copy(
                        abholung = state.abholung,
                        creator = state.creator,
                        respondedByUser = state.respondedByUser,
                        recipients = state.recipients,
                        members = state.members
                    )
                }
        }
        viewModelScope.launch {
            abholungRepo.observeChanges(abholungId).collect { changes ->
                _uiState.value = _uiState.value.copy(changes = changes)
            }
        }
    }

    fun respond(abholungId: String, status: AbholungStatus, note: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            abholungRepo.respondToAbholung(abholungId, status, currentUserId, note)
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun sendReminder(note: String) {
        val abholung = _uiState.value.abholung ?: return
        viewModelScope.launch {
            try {
                functions.getHttpsCallable("sendReminderToRecipients")
                    .call(mapOf("abholungId" to abholung.id, "note" to note))
                    .await()
            } catch (_: Exception) {}
        }
    }
}
