package com.example.health_tracker

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            getUserRole(userId) { userRole ->
                if (userRole != null) {
                    redirectToActivity(userRole)
                }
            }
        } else {
            // Se l'utente non è autenticato, reindirizza alla schermata di login
            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            finish()
        }
    }

    private fun redirectToActivity(userRole: String) {
        when (userRole) {
            "doctor" -> startActivity(Intent(this@MainActivity, DoctorActivity::class.java))
            "caregiver" -> startActivity(Intent(this@MainActivity, CaregiverActivity::class.java))
        }
    }

    private fun getUserRole(userId: String, callback: (String?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(userId)

        userRef.get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val userRole = documentSnapshot.getString("role")
                    callback(userRole)
                    Log.d("HealthTracker", "User role: $userRole")
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("HealthTracker", "Error getting user role: ${e.message}", e)
                callback(null)
            }
    }
}
