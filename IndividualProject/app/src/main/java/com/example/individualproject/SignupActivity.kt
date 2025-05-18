package com.example.individualproject

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.individualproject.databinding.ActivitySignupBinding


class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val authViewModel: AuthViewModel<Any?> by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)


        setupObservers()

        binding.createAccountButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val email = binding.emailEditTextSignup.text.toString().trim()
            val password = binding.passwordEditTextSignup.text.toString()
            val confirmPassword = binding.confirmPasswordEditText.text.toString()

            var isValid = true
            if (name.isEmpty()) {
                binding.nameInputLayout.error = "Name cannot be empty"
                isValid = false
            } else {
                binding.nameInputLayout.error = null
            }

            if (email.isEmpty()) {
                binding.emailInputLayoutSignup.error = "Email cannot be empty"
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailInputLayoutSignup.error = "Invalid email format"
                isValid = false
            } else {
                binding.emailInputLayoutSignup.error = null
            }

            if (password.isEmpty()) {
                binding.passwordInputLayoutSignup.error = "Password cannot be empty"
                isValid = false
            } else if (password.length < 6) {
                binding.passwordInputLayoutSignup.error = "Password must be at least 6 characters"
                isValid = false
            }
            else {
                binding.passwordInputLayoutSignup.error = null
            }

            if (confirmPassword.isEmpty()) {
                binding.confirmPasswordInputLayout.error = "Confirm password cannot be empty"
                isValid = false
            } else if (password != confirmPassword) {
                binding.confirmPasswordInputLayout.error = "Passwords do not match"
                isValid = false
            } else {
                binding.confirmPasswordInputLayout.error = null
            }

            if (isValid) {
                authViewModel.register(name, email, password)
            }
        }

        binding.loginLinkText.setOnClickListener {

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun setupObservers() {
        authViewModel.registrationResult.observe(this) { result ->

            when (result) {
                is AuthResult.Loading -> {
                    binding.createAccountButton.isEnabled = false
                    Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show()
                }
                is AuthResult.Success -> {
                    binding.createAccountButton.isEnabled = true
                    Toast.makeText(this, result.data.message, Toast.LENGTH_LONG).show()

                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
                is AuthResult.Error -> {
                    binding.createAccountButton.isEnabled = true
                    Toast.makeText(this, "Signup Failed: ${result.message}", Toast.LENGTH_LONG).show()
                    if (result.serverError && result.message.contains("Email already registered", ignoreCase = true)) {
                        binding.emailInputLayoutSignup.error = "This email is already registered."
                    }
                }
            }
        }
    }
}