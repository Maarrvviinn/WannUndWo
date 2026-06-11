package de.marvin.wannundwo.ui.screens.abholung

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import de.marvin.wannundwo.data.UserPreferences
import de.marvin.wannundwo.model.Abholung
import de.marvin.wannundwo.model.HaushaltGroup
import de.marvin.wannundwo.model.User
import de.marvin.wannundwo.repository.AbholungRepository
import de.marvin.wannundwo.repository.HaushaltRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class CreateAbholungUiState(
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,
    val members: List<User> = emptyList(),
    val groups: List<HaushaltGroup> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val existingAbholung: Abholung? = null,
    val recentLocations: List<String> = emptyList(),
    val recentTimes: List<String> = emptyList()
)

class CreateAbholungViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val abholungRepo = AbholungRepository()
    private val haushaltRepo = HaushaltRepository()
    private val prefs = UserPreferences(application)

    private val _uiState = MutableStateFlow(CreateAbholungUiState())
    val uiState = _uiState.asStateFlow()

    private var haushaltsId = ""

    init {
        viewModelScope.launch {
            prefs.recentLocations.collect { _uiState.value = _uiState.value.copy(recentLocations = it) }
        }
        viewModelScope.launch {
            prefs.recentTimes.collect { _uiState.value = _uiState.value.copy(recentTimes = it) }
        }
    }

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    fun init(abholungId: String?) {
        viewModelScope.launch {
            val user = haushaltRepo.getUser(auth.currentUser?.uid ?: "") ?: return@launch
            haushaltsId = user.haushaltsId
            // flatMapLatest: wenn Haushalt sich ändert, wird observeMembers neu gestartet
            haushaltRepo.observeHaushalt(haushaltsId)
                .flatMapLatest { haushalt ->
                    _uiState.value = _uiState.value.copy(
                        groups = haushalt?.groups ?: emptyList(),
                        memberNames = haushalt?.memberNames ?: emptyMap()
                    )
                    haushaltRepo.observeMembers(haushalt?.memberIds ?: emptyList())
                }
                .collect { members ->
                    // Creator nie in der Mitgliederliste für Empfänger
                    val otherMembers = members.filter { it.id != currentUserId }
                    _uiState.value = _uiState.value.copy(members = otherMembers)
                }
        }
        if (abholungId != null) {
            viewModelScope.launch {
                val a = abholungRepo.getAbholung(abholungId)
                _uiState.value = _uiState.value.copy(existingAbholung = a)
            }
        }
    }

    fun save(
        time: Date,
        location: String,
        locationLat: Double?,
        locationLng: Double?,
        note: String,
        recipientIds: List<String>
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val userId = auth.currentUser?.uid ?: ""
            // Creator kann sich nicht selbst als Empfänger hinzufügen
            val safeRecipientIds = recipientIds.filter { it != userId }
            val existing = _uiState.value.existingAbholung
            val abholung = (existing ?: Abholung()).copy(
                haushaltsId = haushaltsId,
                creatorId = userId,
                recipientIds = safeRecipientIds,
                time = Timestamp(time),
                location = location,
                locationLat = locationLat,
                locationLng = locationLng,
                note = note
            )
            val result = if (existing != null) abholungRepo.updateAbholung(abholung)
                         else abholungRepo.createAbholung(abholung).map { }
            result.fold(
                onSuccess = {
                    if (location.isNotBlank()) prefs.addRecentLocation(location)
                    prefs.addRecentTime(SimpleDateFormat("HH:mm", Locale.GERMAN).format(time))
                    _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
                },
                onFailure = { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
            )
        }
    }
}
