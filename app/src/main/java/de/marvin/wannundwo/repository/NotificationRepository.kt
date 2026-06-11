package de.marvin.wannundwo.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import de.marvin.wannundwo.model.AppNotification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class NotificationRepository {
    private val db = FirebaseFirestore.getInstance()

    private fun userCol(userId: String) = db.collection("notifications").document(userId).collection("items")

    fun observeNotifications(userId: String): Flow<List<AppNotification>> = callbackFlow {
        val listener = userCol(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject<AppNotification>()?.copy(id = it.id) } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun observeUnreadCount(userId: String): Flow<Int> = callbackFlow {
        val listener = userCol(userId)
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, _ -> trySend(snap?.size() ?: 0) }
        awaitClose { listener.remove() }
    }

    suspend fun markAllRead(userId: String) {
        val batch = db.batch()
        val docs = userCol(userId).whereEqualTo("read", false).get().await()
        docs.forEach { batch.update(it.reference, "read", true) }
        batch.commit().await()
    }
}
