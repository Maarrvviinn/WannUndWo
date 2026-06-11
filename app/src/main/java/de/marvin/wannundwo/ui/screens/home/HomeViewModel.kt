package de.marvin.wannundwo.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import de.marvin.wannundwo.model.Abholung
import de.marvin.wannundwo.model.AbholungStatus
import de.marvin.wannundwo.model.Haushalt
import de.marvin.wannundwo.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import de.marvin.wannundwo.repository.AbholungRepository
import de.marvin.wannundwo.repository.HaushaltRepository
import de.marvin.wannundwo.repository.NotificationRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val haushalt: Haushalt? = null,
    val abholungen: List<Abholung> = emptyList(),
    val history: List<Abholung> = emptyList(),
    val members: List<User> = emptyList(),
    val currentUser: User? = null,
    val unreadCount: Int = 0,
    val needsHaushalt: Boolean = false,
    val isRefreshing: Boolean = false
)

class HomeViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val haushaltRepo = HaushaltRepository()
    private val abholungRepo = AbholungRepository()
    private val notifRepo = NotificationRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    private val userId get() = auth.currentUser?.uid ?: ""

    init { load() }

    private fun load() {
        viewModelScope.launch {
            haushaltRepo.observeUser(userId).collect { user ->
                if (user == null) { _uiState.value = HomeUiState(needsHaushalt = true); return@collect }
                val haushaltsId = user.haushaltsId
                if (haushaltsId.isBlank()) { _uiState.value = HomeUiState(needsHaushalt = true); return@collect }
                _uiState.value = _uiState.value.copy(currentUser = user, needsHaushalt = false)
                loadHaushalt(haushaltsId)
                loadAbholungen(haushaltsId)
                loadHistory(haushaltsId)
                loadUnread()
            }
        }
    }

    private fun loadHaushalt(id: String) {
        viewModelScope.launch {
            haushaltRepo.observeHaushalt(id).collect { haushalt ->
                _uiState.value = _uiState.value.copy(haushalt = haushalt)
                haushalt?.memberIds?.let { loadMembers(it) }
            }
        }
    }

    private fun loadMembers(ids: List<String>) {
        viewModelScope.launch {
            haushaltRepo.observeMembers(ids).collect { members ->
                _uiState.value = _uiState.value.copy(members = members)
            }
        }
    }

    private val ticker = flow { while (true) { emit(Unit); delay(60_000L) } }

    private fun loadAbholungen(haushaltsId: String) {
        viewModelScope.launch {
            combine(abholungRepo.observeAllForHaushalt(haushaltsId), ticker) { list, _ ->
                val now = System.currentTimeMillis()
                val uid = userId
                list.filter { a ->
                    // Auto-hide ANGENOMMEN items 10+ minutes after pickup time
                    if (a.status == AbholungStatus.ANGENOMMEN.name) {
                        val pickupTimeMs = a.time.toDate().time
                        val timeSincePickup = (now - pickupTimeMs) / 1000 / 60  // minutes
                        if (timeSincePickup > 10) return@filter false  // Hide from main screen
                    }
                    
                    when (a.status) {
                        // AUSSTEHEND: Creator sieht es immer; Empfänger sehen es
                        AbholungStatus.AUSSTEHEND.name ->
                            a.creatorId == uid || uid in a.recipientIds
                        // ANGENOMMEN: Creator sieht es; Empfänger wenn sie es angenommen haben
                        AbholungStatus.ANGENOMMEN.name ->
                            a.creatorId == uid || (uid in a.recipientIds && uid == a.respondedBy)
                        // ABGELEHNT: nur für Creator sichtbar (nicht für den der abgelehnt hat)
                        AbholungStatus.ABGELEHNT.name ->
                            a.creatorId == uid
                        else -> false
                    }
                }.sortedBy { it.time.seconds }
            }.collect { list ->
                _uiState.value = _uiState.value.copy(abholungen = list)
            }
        }
    }

    fun respondToAbholung(abholungId: String, status: AbholungStatus, note: String) {
        viewModelScope.launch {
            abholungRepo.respondToAbholung(abholungId, status, userId, note)
        }
    }

    private fun loadHistory(haushaltsId: String) {
        viewModelScope.launch {
            abholungRepo.observeHistory(haushaltsId).collect { list ->
                _uiState.value = _uiState.value.copy(history = list)
            }
        }
    }

    private fun loadUnread() {
        viewModelScope.launch {
            notifRepo.observeUnreadCount(userId).collect { count ->
                _uiState.value = _uiState.value.copy(unreadCount = count)
            }
        }
    }

    fun deleteAbholung(id: String) {
        viewModelScope.launch { abholungRepo.deleteAbholung(id) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            kotlinx.coroutines.delay(800)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }
}
