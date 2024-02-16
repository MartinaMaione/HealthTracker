package com.example.health_tracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : ComponentActivity() {
    var emailEditText: EditText? = null
    var passwordEditText: EditText? = null
    var progressBar: ProgressBar? = null
    var mAuth: FirebaseAuth? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        FirebaseApp.initializeApp(this)
        mAuth = FirebaseAuth.getInstance()
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val signUpButton = findViewById<Button>(R.id.signUpButton)
        progressBar = findViewById(R.id.progressBar)
        loginButton.setOnClickListener { view: View? -> loginUser() }
        signUpButton.setOnClickListener { view: View? ->
            startActivity(
                Intent(
                    this@LoginActivity,
                    SignupActivity::class.java
                )
            )
        }
    }

    fun loginUser() {
        val email = emailEditText!!.text.toString().trim { it <= ' ' }
        val password = passwordEditText!!.text.toString().trim { it <= ' ' }
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this@LoginActivity, R.string.credentials, Toast.LENGTH_SHORT).show()
            return
        }
        progressBar!!.visibility = View.VISIBLE
        mAuth!!.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task: Task<AuthResult?> ->
                progressBar!!.visibility = View.GONE
                if (task.isSuccessful) {
                    Toast.makeText(this@LoginActivity, R.string.auth_done, Toast.LENGTH_SHORT)
                        .show()
                    val homeIntent = Intent(this@LoginActivity, MainActivity::class.java)
                    startActivity(homeIntent)
                    finish()
                } else {
                    // Si è verificato un errore durante l'autenticazione
                    Toast.makeText(this@LoginActivity, R.string.auth_failed, Toast.LENGTH_SHORT)
                        .show()
                }
            }
    }
}

