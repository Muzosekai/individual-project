package com.example.individualproject

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.individualproject.databinding.ActivityLoginBinding


class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authViewModel: AuthViewModel<Any?> by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)


        setupObservers()

        binding.loginButton.setOnClickListener {
            val email = binding.emailEditText.text.toString().trim()
            val password = binding.passwordEditText.text.toString()

            var isValid = true
            if (email.isEmpty()) {
                binding.emailInputLayout.error = "Email cannot be empty"
                isValid = false
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailInputLayout.error = "Invalid email format"
                isValid = false
            }
            else {
                binding.emailInputLayout.error = null
            }

            if (password.isEmpty()) {
                binding.passwordInputLayout.error = "Password cannot be empty"
                isValid = false
            } else {
                binding.passwordInputLayout.error = null
            }

            if (isValid) {
                authViewModel.login(email, password)
            }
        }

        binding.signupButton.setOnClickListener {
            println("Signup button clicked - Navigate to Signup Screen")
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }

        binding.forgotPasswordText.setOnClickListener {

            showForgotPasswordDialog()
        }
    }

    private fun setupObservers() {
        authViewModel.loginResult.observe(this) { result ->

            when (result) {
                is AuthResult.Loading -> {
                    binding.loginButton.isEnabled = false // Disable button while loading
                    Toast.makeText(this, "Logging in...", Toast.LENGTH_SHORT).show()
                }
                is AuthResult.Success -> {
                    binding.loginButton.isEnabled = true
                    Toast.makeText(this, result.data.message, Toast.LENGTH_LONG).show()

                    println("Login Successful. Token: ${result.data.token}, User: ${result.data.user?.name}")
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()
                }
                is AuthResult.Error -> {
                    binding.loginButton.isEnabled = true
                    Toast.makeText(this, "Login Failed: ${result.message}", Toast.LENGTH_LONG).show()

                    if (result.serverError && (result.message.contains("Invalid email or password", ignoreCase = true) ||
                                result.message.contains("Email and password are required", ignoreCase = true))) {
                        binding.passwordInputLayout.error = result.message
                    }
                }
            }
        }

        authViewModel.forgotPasswordResult.observe(this) { result ->

            when (result) {
                is AuthResult.Loading -> {
                    Toast.makeText(this, "Processing...", Toast.LENGTH_SHORT).show()
                }
                is AuthResult.Success -> {
                    Toast.makeText(this, result.data.message, Toast.LENGTH_LONG).show()
                }
                is AuthResult.Error -> {
                    Toast.makeText(this, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showForgotPasswordDialog() {

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Forgot Password")
        val input = EditText(this)
        input.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        input.hint = "Enter your email"
        builder.setView(input)
        builder.setPositiveButton("Submit") { dialog, _ ->
            val email = input.text.toString().trim()
            if (email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                authViewModel.forgotPassword(email)
            } else {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.show()
    }
}