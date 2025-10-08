package com.example.nirman_raipur_app

import com.example.nirman_raipur_app.ui.WorkProgressActivity
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.example.nirman_raipur_app.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init Firebase Auth
        auth = FirebaseAuth.getInstance()

        // If already logged in, go straight to Home
        auth.currentUser?.let {
            goToHome()
            return
        }

        // Initialize UI
        setupHindiTitle()
        setupLoginLogic()
        setupBackgroundAnimations()
    }

    private fun setupLoginLogic() {
        binding.btnSignIn.setOnClickListener {
            val email = binding.idInput.text?.toString()?.trim() ?: ""
            val password = binding.passwordInput.text?.toString()?.trim() ?: ""

            var valid = true
            if (email.isEmpty()) {
                binding.idLayout.error = "Enter your email"
                valid = false
            } else {
                binding.idLayout.error = null
            }

            if (password.length < 6) {
                binding.passwordLayout.error = "Password must be at least 6 characters"
                valid = false
            } else {
                binding.passwordLayout.error = null
            }

            if (!valid) return@setOnClickListener

            binding.btnSignIn.isEnabled = false
            Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()

            // Try to log in with Firebase
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    binding.btnSignIn.isEnabled = true
                    if (task.isSuccessful) {
                        goToHome()
                    } else {
                        // If login fails, try creating account
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { reg ->
                                if (reg.isSuccessful) {
                                    goToHome()
                                } else {
                                    Toast.makeText(
                                        this,
                                        reg.exception?.localizedMessage ?: "Login failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    }
                }
        }
    }

    private fun goToHome() {
        val intent = Intent(this, WorkProgressActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun setupBackgroundAnimations() {
        val glow: ImageView? = try {
            binding.glowBig
        } catch (t: Throwable) {
            findViewById(R.id.glow_big)
        }

        glow?.let {
            ObjectAnimator.ofFloat(it, "translationY", -60f, -30f).apply {
                duration = 7000L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun setupHindiTitle() {
        val titleTv: TextView = try {
            binding.titleHindi
        } catch (t: Throwable) {
            findViewById(R.id.title_hindi)
        }

        val font = ResourcesCompat.getFont(this, R.font.devanagari_stylish)
        if (font != null) {
            titleTv.typeface = font
        } else {
            Log.w(TAG, "devanagari_stylish font not found — falling back to default")
        }

        titleTv.post {
            try {
                val paint: Paint = titleTv.paint
                val text = titleTv.text.toString()
                val textWidth = paint.measureText(text)
                val extra = textWidth * 0.5f
                val shader = LinearGradient(
                    -extra, 0f, textWidth + extra, 0f,
                    intArrayOf(
                        resources.getColor(R.color.light_blue, theme),
                        resources.getColor(R.color.mid_blue, theme),
                        resources.getColor(R.color.dark_blue, theme)
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )

                paint.shader = shader
                paint.style = Paint.Style.FILL_AND_STROKE
                paint.strokeWidth = resources.displayMetrics.density * 0.8f
                titleTv.invalidate()

                val matrix = Matrix()
                val anim = ValueAnimator.ofFloat(-extra, textWidth + extra).apply {
                    duration = 2200L
                    repeatMode = ValueAnimator.RESTART
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener { va ->
                        val translateX = va.animatedValue as Float
                        matrix.reset()
                        matrix.setTranslate(translateX, 0f)
                        shader.setLocalMatrix(matrix)
                        titleTv.invalidate()
                    }
                    start()
                }

                titleTv.scaleX = 0.86f
                titleTv.scaleY = 0.86f
                titleTv.alpha = 0f
                titleTv.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(700L)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()

            } catch (e: Exception) {
                Log.w(TAG, "Failed to animate Hindi title: ${e.message}")
            }
        }
    }
}