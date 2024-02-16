package com.example.health_tracker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        btnSignUp.setOnClickListener {
            val email = findViewById<EditText>(R.id.emailEditText).text.toString()
            val password = findViewById<EditText>(R.id.passwordEditText).text.toString()
            val name = findViewById<EditText>(R.id.edtName).text.toString()
            val surname = findViewById<EditText>(R.id.edtSurname).text.toString()
            val phoneNumber = findViewById<EditText>(R.id.edtPhoneNumber).text.toString()
            val address = findViewById<EditText>(R.id.edtAddress).text.toString()
            val radioGroupRole = findViewById<RadioGroup>(R.id.radioGroupRole)
            val radioButtonCaregiver = findViewById<RadioButton>(R.id.radioButtonCaregiver)
            val radioButtonDoctor = findViewById<RadioButton>(R.id.radioButtonDoctor)

            val role = when (radioGroupRole.checkedRadioButtonId) {
                radioButtonCaregiver.id -> "caregiver"
                radioButtonDoctor.id -> "doctor"
                else -> "caregiver" // Valore predefinito se nessun RadioButton è selezionato
            }

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password) || TextUtils.isEmpty(name) ||
                TextUtils.isEmpty(surname) || TextUtils.isEmpty(phoneNumber) || TextUtils.isEmpty(
                    address)
            ) {
                Toast.makeText(this@SignupActivity, R.string.complete_form, Toast.LENGTH_SHORT)
                    .show()
            } else {
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this@SignupActivity) { task ->
                        if (task.isSuccessful) {
                            val user: FirebaseUser? = FirebaseAuth.getInstance().currentUser

                            if (user != null) {
                                val userId = user.uid
                                val db = FirebaseFirestore.getInstance()
                                val userRef = db.collection("users").document(userId)
                                val displayName = "$name $surname"
                                val user = User(displayName, phoneNumber, address, email, role , userId)
                                userRef.set(user)
                                    .addOnSuccessListener {
                                        Toast.makeText(
                                            this@SignupActivity,
                                            R.string.Signup_done,
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        // -> LoginActivity
                                        val loginIntent =
                                            Intent(this@SignupActivity, LoginActivity::class.java)
                                        startActivity(loginIntent)
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(
                                            this@SignupActivity,
                                            getString(R.string.Firestore_error) + e.message,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            } else {
                                Toast.makeText(
                                    this@SignupActivity,
                                    getString(R.string.User_null),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                this@SignupActivity,
                                getString(R.string.Signup_failed) + task.exception?.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            }
        }

        // Gestione del pulsante per tornare indietro alla pagina di login
        val btnBackToLogin = findViewById<Button>(R.id.btnBackToLogin)
        btnBackToLogin.setOnClickListener {
            val intent = Intent(this@SignupActivity, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
