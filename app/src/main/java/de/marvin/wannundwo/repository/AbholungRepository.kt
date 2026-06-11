package de.marvin.wannundwo.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.toObject
import de.marvin.wannundwo.model.Abholung
import de.marvin.wannundwo.model.AbholungChange
import de.marvin.wannundwo.model.AbholungStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AbholungRepository {
    private val db = FirebaseFirestore.getInstance()
    private val col = db.collection("abholungen")

    /** Alle Abholungen des Haushalts ohne Filter – Filterung passiert im ViewModel */
    fun observeAllForHaushalt(haushaltsId: String): Flow<List<Abholung>> = callbackFlow {
        val listener = col
            .whereEqualTo("haushaltsId", haushaltsId)
            .addSnapshotListener { snap, _ ->
                trySend(
                    snap?.documents?.mapNotNull { it.toObject<Abholung>()?.copy(id = it.id) }
                        ?: emptyList()
                )
            }
        awaitClose { listener.remove() }
    }

    // Legacy – bleibt für etwaige Direktnutzung
    fun observeAbholungen(haushaltsId: String): Flow<List<Abholung>> = observeAllForHaushalt(haushaltsId)

    fun observeHistory(haushaltsId: String): Flow<List<Abholung>> = callbackFlow {
        val listener = col
            .whereEqualTo("haushaltsId", haushaltsId)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents
                    ?.mapNotNull { it.toObject<Abholung>()?.copy(id = it.id) }
                    ?.sortedByDescending { it.createdAt.seconds }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun createAbholung(abholung: Abholung): Result<Abholung> {
        return try {
            val ref = col.add(abholung).await()
            Result.success(abholung.copy(id = ref.id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAbholung(abholung: Abholung): Result<Unit> {
        return try {
            col.document(abholung.id).set(abholung.copy(updatedAt = Timestamp.now())).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun respondToAbholung(
        abholungId: String,
        status: AbholungStatus,
        respondedBy: String,
        responseNote: String
    ): Result<Unit> {
        return try {
            col.document(abholungId).update(
                mapOf(
                    "status" to status.name,
                    "respondedBy" to respondedBy,
                    "responseNote" to responseNote,
                    "respondedAt" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAbholung(id: String): Result<Unit> {
        return try {
            col.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAbholung(id: String): Abholung? {
        return try {
            val doc = col.document(id).get().await()
            doc.toObject<Abholung>()?.copy(id = doc.id)
        } catch (e: Exception) { null }
    }

    fun observeAbholung(id: String): Flow<Abholung?> = callbackFlow {
        val listener = col.document(id).addSnapshotListener { snap, _ ->
            trySend(snap?.toObject<Abholung>()?.copy(id = snap.id))
        }
        awaitClose { listener.remove() }
    }

    fun observeChanges(abholungId: String): Flow<List<AbholungChange>> = callbackFlow {
        val listener = col.document(abholungId).collection("changes")
            .orderBy("changedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull {
                    it.toObject<AbholungChange>()?.copy(id = it.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun addChange(abholungId: String, change: AbholungChange) {
        try {
            col.document(abholungId).collection("changes").add(change).await()
        } catch (_: Exception) {}
    }
}
