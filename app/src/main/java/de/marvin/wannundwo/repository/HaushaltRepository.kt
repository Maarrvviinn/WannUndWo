package de.marvin.wannundwo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import de.marvin.wannundwo.model.Haushalt
import de.marvin.wannundwo.model.HaushaltGroup
import de.marvin.wannundwo.model.Permissions
import de.marvin.wannundwo.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class HaushaltRepository {
    private val db = FirebaseFirestore.getInstance()
    private val haushalteCol = db.collection("haushalte")
    private val usersCol = db.collection("users")

    suspend fun createHaushalt(name: String, adminId: String, adminName: String = ""): Result<Haushalt> {
        return try {
            val code = generateInviteCode()
            val haushalt = Haushalt(
                name = name,
                adminId = adminId,
                memberIds = listOf(adminId),
                memberNames = if (adminName.isNotBlank()) mapOf(adminId to adminName) else emptyMap(),
                inviteCode = code
            )
            val ref = haushalteCol.add(haushalt).await()
            Result.success(haushalt.copy(id = ref.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinHaushalt(code: String, userId: String, userName: String = ""): Result<Haushalt> {
        return try {
            val snap = haushalteCol.whereEqualTo("inviteCode", code).get().await()
            if (snap.isEmpty) return Result.failure(Exception("Ungültiger Einladungscode"))
            val doc = snap.documents.first()
            val haushalt = doc.toObject<Haushalt>()!!.copy(id = doc.id)
            val updatedMembers = (haushalt.memberIds + userId).distinct()
            val updatedNames = if (userName.isNotBlank()) haushalt.memberNames + (userId to userName) else haushalt.memberNames
            haushalteCol.document(haushalt.id).update(
                mapOf("memberIds" to updatedMembers, "memberNames" to updatedNames)
            ).await()
            Result.success(haushalt.copy(memberIds = updatedMembers, memberNames = updatedNames))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeHaushalt(haushaltsId: String): Flow<Haushalt?> = callbackFlow {
        val listener = haushalteCol.document(haushaltsId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObject<Haushalt>()?.copy(id = snap.id))
            }
        awaitClose { listener.remove() }
    }

    suspend fun getUser(userId: String): User? {
        return try {
            val doc = usersCol.document(userId).get().await()
            doc.toObject<User>()?.copy(id = doc.id)
        } catch (e: Exception) { null }
    }

    fun observeUser(userId: String): Flow<User?> = callbackFlow {
        val listener = usersCol.document(userId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObject<User>()?.copy(id = snap.id))
            }
        awaitClose { listener.remove() }
    }

    fun observeMembers(memberIds: List<String>): Flow<List<User>> = callbackFlow {
        if (memberIds.isEmpty()) { trySend(emptyList()); close(); return@callbackFlow }
        val listener = usersCol.whereIn("__name__", memberIds)
            .addSnapshotListener { snap, _ ->
                val users = snap?.documents?.mapNotNull { it.toObject<User>()?.copy(id = it.id) } ?: emptyList()
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    suspend fun createUserDoc(userId: String, name: String, email: String, haushaltsId: String, permissions: Permissions = Permissions()) {
        val user = User(id = userId, name = name, email = email, haushaltsId = haushaltsId, permissions = permissions)
        usersCol.document(userId).set(user).await()
    }

    suspend fun updateUserFcmToken(userId: String, token: String) {
        usersCol.document(userId).update("fcmToken", token).await()
    }

    suspend fun updatePermissions(userId: String, permissions: Permissions) {
        usersCol.document(userId).update("permissions", permissions).await()
    }

    suspend fun updateNotificationPreference(userId: String, key: String, enabled: Boolean) {
        usersCol.document(userId).update("notificationPreferences.$key", enabled).await()
    }

    suspend fun updateHaushaltName(haushaltsId: String, name: String) {
        haushalteCol.document(haushaltsId).update("name", name).await()
    }

    suspend fun addMemberToHaushalt(haushaltsId: String, userId: String, userName: String = "") {
        val updates = mutableMapOf<String, Any>(
            "memberIds" to com.google.firebase.firestore.FieldValue.arrayUnion(userId)
        )
        if (userName.isNotBlank()) updates["memberNames.$userId"] = userName
        haushalteCol.document(haushaltsId).update(updates).await()
    }

    suspend fun updateMemberNameInHaushalt(haushaltsId: String, userId: String, name: String) {
        haushalteCol.document(haushaltsId).update("memberNames.$userId", name).await()
    }

    suspend fun createGroup(haushaltsId: String, groupName: String): String {
        val groupId = java.util.UUID.randomUUID().toString()
        val haushalt = observeHaushalt(haushaltsId).first() ?: return groupId
        val newGroups = haushalt.groups + HaushaltGroup(id = groupId, name = groupName)
        haushalteCol.document(haushaltsId).update("groups", newGroups).await()
        return groupId
    }

    suspend fun deleteGroup(haushaltsId: String, groupId: String) {
        val haushalt = observeHaushalt(haushaltsId).first() ?: return
        val newGroups = haushalt.groups.filter { it.id != groupId }
        haushalteCol.document(haushaltsId).update("groups", newGroups).await()
    }

    suspend fun updateGroupMembers(haushaltsId: String, groupId: String, memberIds: List<String>) {
        val haushalt = observeHaushalt(haushaltsId).first() ?: return
        val newGroups = haushalt.groups.map { if (it.id == groupId) it.copy(memberIds = memberIds) else it }
        haushalteCol.document(haushaltsId).update("groups", newGroups).await()
    }

    suspend fun updateGroup(haushaltsId: String, groupId: String, name: String, memberIds: List<String>) {
        val haushalt = observeHaushalt(haushaltsId).first() ?: return
        val newGroups = haushalt.groups.map {
            if (it.id == groupId) it.copy(name = name, memberIds = memberIds) else it
        }
        haushalteCol.document(haushaltsId).update("groups", newGroups).await()
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    suspend fun removeMemberFromHaushalt(haushaltsId: String, userId: String) {
        val updates = mapOf(
            "memberIds" to com.google.firebase.firestore.FieldValue.arrayRemove(userId),
            "memberNames.$userId" to com.google.firebase.firestore.FieldValue.delete()
        )
        haushalteCol.document(haushaltsId).update(updates).await()
    }

    suspend fun updateAllMembersToken(haushaltsId: String, token: String) {
        val haushalt = haushalteCol.document(haushaltsId).get().await()
            .toObject(de.marvin.wannundwo.model.Haushalt::class.java) ?: return
        haushalt.memberIds.forEach { uid ->
            usersCol.document(uid).update("fcmToken", token).await()
        }
    }
}
