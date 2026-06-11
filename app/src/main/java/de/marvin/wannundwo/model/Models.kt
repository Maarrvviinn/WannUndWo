package de.marvin.wannundwo.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class HaushaltGroup(
    val id: String = "",
    val name: String = "",
    val memberIds: List<String> = emptyList()
)

data class Haushalt(
    @DocumentId val id: String = "",
    val name: String = "",
    val adminId: String = "",
    val memberIds: List<String> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val groups: List<HaushaltGroup> = emptyList(),
    val inviteCode: String = "",
    val creationDate: Timestamp = Timestamp.now()
)

data class Permissions(
    val canCreateAbholung: Boolean = true,
    val canEditAbholung: Boolean = true,
    val canDeleteAbholung: Boolean = false
)

data class NotificationPreferences(
    val abholungErstellt: Boolean = true,
    val abholungGeaendert: Boolean = true,
    val abholungAngenommen: Boolean = true,
    val abholungAbgelehnt: Boolean = true
)

data class User(
    @DocumentId val id: String = "",
    val name: String = "",
    val email: String = "",
    val haushaltsId: String = "",
    val permissions: Permissions = Permissions(),
    val fcmToken: String = "",
    val notificationPreferences: NotificationPreferences = NotificationPreferences()
)

enum class AbholungStatus { AUSSTEHEND, ANGENOMMEN, ABGELEHNT }

data class Abholung(
    @DocumentId val id: String = "",
    val haushaltsId: String = "",
    val creatorId: String = "",
    val recipientIds: List<String> = emptyList(),
    val time: Timestamp = Timestamp.now(),
    val location: String = "",
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val note: String = "",
    val status: String = AbholungStatus.AUSSTEHEND.name,
    val responseNote: String = "",
    val respondedBy: String = "",
    val respondedAt: Timestamp? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()
)

data class AbholungChange(
    @DocumentId val id: String = "",
    val field: String = "",      // "time" | "location" | "note" | "status"
    val oldValue: String = "",
    val newValue: String = "",
    val changedBy: String = "",
    val changedAt: Timestamp = Timestamp.now()
)

enum class NotificationType { ABHOLUNG_ERSTELLT, ABHOLUNG_GEAENDERT, ABHOLUNG_ANGENOMMEN, ABHOLUNG_ABGELEHNT }

data class AppNotification(
    @DocumentId val id: String = "",
    val type: String = "",
    val message: String = "",
    val abholungId: String = "",
    val read: Boolean = false,
    val createdAt: Timestamp = Timestamp.now()
)
