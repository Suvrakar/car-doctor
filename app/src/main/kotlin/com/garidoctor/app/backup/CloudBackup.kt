package com.garidoctor.app.backup

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Optional cloud backup on the Firebase free tier: one Firestore document per user holding the
 * backup JSON text (no photos → stays far under the 1 MB document limit for normal use).
 * Works only when google-services.json is configured; [available] is false otherwise.
 */
class CloudBackup(private val context: Context) {
    val available: Boolean
        get() = try { FirebaseApp.getApps(context).isNotEmpty() } catch (_: Exception) { false }

    private val auth get() = FirebaseAuth.getInstance()

    val userEmail: String? get() = if (available) auth.currentUser?.email else null

    suspend fun signIn(email: String, password: String, create: Boolean) {
        if (create) auth.createUserWithEmailAndPassword(email.trim(), password).await()
        else auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun signOut() { if (available) auth.signOut() }

    suspend fun upload(json: String, epochDay: Long) {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        require(json.length < 900_000) { "Backup too large for one document" }
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf("backup" to json, "updatedEpochDay" to epochDay, "format" to 1)).await()
    }

    suspend fun download(): String? {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        return FirebaseFirestore.getInstance().collection("users").document(uid).get().await().getString("backup")
    }
}
