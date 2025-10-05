package com.example.nirman_raipur_app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class WorkProgress : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.work_progress)  // links to XML layout

        auth = FirebaseAuth.getInstance()

        val welcomeTv = findViewById<TextView>(R.id.tvWelcome)
        val logoutBtn = findViewById<Button>(R.id.btnLogout)

        // Show the user's email (optional)
        val user = auth.currentUser
        welcomeTv.text = "Welcome, ${user?.email ?: "Guest"} 🎉"

        // Logout button
        logoutBtn.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
