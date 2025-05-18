package com.example.individualproject

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.individualproject.databinding.ActivityCareerSuggestionsBinding
import kotlinx.coroutines.launch

class CareerSuggestionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCareerSuggestionsBinding
    private lateinit var careersAdapter: SuggestedCareersAdapter
    private lateinit var skillGapsAdapter: SkillGapsAdapter

    // Store last inputs for refresh functionality
    private var lastInterests: String = ""
    private var lastSkills: String = ""
    private var lastGoals: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCareerSuggestionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Toolbar setup
        setSupportActionBar(binding.toolbarCareerSuggestions)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "AI Career Suggestions"

        // Setup RecyclerViews
        careersAdapter = SuggestedCareersAdapter(emptyList())
        binding.recyclerViewCareerPaths.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewCareerPaths.adapter = careersAdapter
        binding.recyclerViewCareerPaths.isNestedScrollingEnabled = false // Good for ScrollView parent

        skillGapsAdapter = SkillGapsAdapter(emptyList())
        binding.recyclerViewSkillGaps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewSkillGaps.adapter = skillGapsAdapter
        binding.recyclerViewSkillGaps.isNestedScrollingEnabled = false

        // Button Click Listeners
        binding.buttonGetSuggestions.setOnClickListener {
            handleGetSuggestionsClick()
        }

        binding.buttonRefreshSuggestions.setOnClickListener {
            if (lastInterests.isNotEmpty() && lastSkills.isNotEmpty()) {
                fetchSuggestionsFromBackend(lastInterests, lastSkills, lastGoals)
            } else {
                // Fallback to current text if last inputs are somehow empty
                handleGetSuggestionsClick()
            }
        }

        // Initially hide suggestion sections and refresh button
        setSuggestionSectionsVisibility(View.GONE)
        binding.buttonRefreshSuggestions.visibility = View.GONE
        binding.textViewAIError.visibility = View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle arrow click
        if (item.itemId == android.R.id.home) {
            navigateToDashboard()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun handleGetSuggestionsClick() {
        val interests = binding.editTextUserInterests.text.toString().trim()
        val skills = binding.editTextUserSkills.text.toString().trim()
        val goals = binding.editTextUserGoals.text.toString().trim().ifEmpty { null }

        if (interests.isNotEmpty() && skills.isNotEmpty()) {
            lastInterests = interests
            lastSkills = skills
            lastGoals = goals
            fetchSuggestionsFromBackend(interests, skills, goals)
        } else {
            Toast.makeText(this, "Please enter your interests and skills.", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchSuggestionsFromBackend(userInterests: String, userSkills: String, userGoals: String?) {
        binding.progressBarAISuggestions.visibility = View.VISIBLE
        binding.buttonGetSuggestions.isEnabled = false
        binding.buttonRefreshSuggestions.isEnabled = false
        binding.textViewAIError.visibility = View.GONE
        setSuggestionSectionsVisibility(View.GONE)

        val requestBody = AICareerSuggestionRequest(
            interests = userInterests,
            skills = userSkills,
            goals = userGoals
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getAICareerSuggestions(requestBody)

                if (response.isSuccessful && response.body() != null) {
                    val flaskResponse = response.body()!!
                    if (!flaskResponse.error && flaskResponse.suggestions != null) {
                        updateUIWithSuggestions(flaskResponse.suggestions)
                    } else {
                        val errorMessage = flaskResponse.message ?: "Failed to get suggestions from server."
                        Log.e("CareerSuggestions", "Backend Error: $errorMessage")
                        if (flaskResponse.rawAiResponse != null) {
                            Log.e("CareerSuggestions", "Raw AI response from server for debugging: ${flaskResponse.rawAiResponse}")
                            updateUIWithError("$errorMessage\n(Debug AI Output: ${flaskResponse.rawAiResponse.take(200)}...)")
                        } else {
                            updateUIWithError(errorMessage)
                        }
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error connecting to server."
                    Log.e("CareerSuggestions", "API Call to Flask Failed: ${response.code()} - $errorBody")
                    updateUIWithError("Error ${response.code()}: $errorBody")
                }
            } catch (e: Exception) {
                Log.e("CareerSuggestions", "Exception calling Flask backend: ${e.message}", e)
                updateUIWithError("Network or unexpected error: ${e.localizedMessage}")
            } finally {
                binding.progressBarAISuggestions.visibility = View.GONE
                binding.buttonGetSuggestions.isEnabled = true
                binding.buttonRefreshSuggestions.isEnabled = true
                binding.buttonRefreshSuggestions.visibility = View.VISIBLE
            }
        }
    }

    private fun updateUIWithSuggestions(suggestions: ParsedAISuggestions) {
        binding.textViewAIError.visibility = View.GONE
        var hasContent = false

        suggestions.suggestedCareers?.let {
            if (it.isNotEmpty()) {
                binding.textViewAISuggestionsTitle.visibility = View.VISIBLE
                binding.recyclerViewCareerPaths.visibility = View.VISIBLE
                careersAdapter.updateData(it)
                hasContent = true
            }
        }

        suggestions.skillGaps?.let {
            if (it.isNotEmpty()) {
                binding.textViewAISkillGapsTitle.visibility = View.VISIBLE
                binding.recyclerViewSkillGaps.visibility = View.VISIBLE
                skillGapsAdapter.updateData(it)
                hasContent = true
            }
        }

        suggestions.generalAdvice?.let {
            if (it.isNotBlank()) {
                binding.textViewAIGeneralAdviceTitle.visibility = View.VISIBLE
                binding.textViewGeneralAdvice.visibility = View.VISIBLE
                binding.textViewGeneralAdvice.text = it
                hasContent = true
            }
        }
        if (!hasContent) {
            updateUIWithError("AI provided no specific suggestions based on the input.")
        }
    }

    private fun updateUIWithError(errorMessage: String) {
        setSuggestionSectionsVisibility(View.GONE)
        binding.textViewAIError.text = errorMessage
        binding.textViewAIError.visibility = View.VISIBLE
    }

    private fun setSuggestionSectionsVisibility(visibility: Int) {
        binding.textViewAISuggestionsTitle.visibility = visibility
        binding.recyclerViewCareerPaths.visibility = visibility
        binding.textViewAISkillGapsTitle.visibility = visibility
        binding.recyclerViewSkillGaps.visibility = visibility
        binding.textViewAIGeneralAdviceTitle.visibility = visibility
        binding.textViewGeneralAdvice.visibility = visibility
    }

}