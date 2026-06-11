package de.marvin.wannundwo.ui.screens.haushalt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.marvin.wannundwo.repository.HaushaltRepository
import kotlinx.coroutines.launch

class GroupViewModel : ViewModel() {
    private val haushaltRepo = HaushaltRepository()

    fun createGroup(haushaltsId: String, name: String, memberIds: Set<String>) {
        viewModelScope.launch {
            val groupId = haushaltRepo.createGroup(haushaltsId, name)
            if (memberIds.isNotEmpty()) {
                haushaltRepo.updateGroupMembers(haushaltsId, groupId, memberIds.toList())
            }
        }
    }

    fun deleteGroup(haushaltsId: String, groupId: String) {
        viewModelScope.launch {
            haushaltRepo.deleteGroup(haushaltsId, groupId)
        }
    }

    fun updateGroupMembers(haushaltsId: String, groupId: String, memberIds: List<String>) {
        viewModelScope.launch {
            haushaltRepo.updateGroupMembers(haushaltsId, groupId, memberIds)
        }
    }

    fun updateGroup(haushaltsId: String, groupId: String, name: String, memberIds: List<String>) {
        viewModelScope.launch {
            haushaltRepo.updateGroup(haushaltsId, groupId, name, memberIds)
        }
    }
}
