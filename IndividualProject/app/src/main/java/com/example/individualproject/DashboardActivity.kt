package com.example.individualproject

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.individualproject.databinding.ActivityDashboardBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var deadlineAdapter: DeadlineAdapter
    private var deadlinesList = mutableListOf<Deadline>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.dashboard_title)

        setupDeadlinesRecyclerView()
        loadDashboardData()


        binding.applyNowButton.setOnClickListener {
            val intent = Intent(this, InternshipListActivity::class.java)
            startActivity(intent)
        }
        binding.viewPortfolioButton.setOnClickListener {
            val intent = Intent(this, PortfolioActivity::class.java)
            startActivity(intent)
        }
        binding.getSuggestionsButton.setOnClickListener {
             val intent = Intent(this, CareerSuggestionsActivity::class.java)
             startActivity(intent)

        }
    }

    private fun setupDeadlinesRecyclerView() {
        deadlineAdapter = DeadlineAdapter(deadlinesList)
        binding.deadlinesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = deadlineAdapter
        }
        println("RecyclerView for deadlines setup")
    }

    private fun loadDashboardData() {

        println("Loading dashboard data...")

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getDashboardData() // API
                if (response.isSuccessful) {
                    response.body()?.let { data ->
                        if (!data.error) {

                            binding.resumeStatusText.text = "Resume: ${data.resumeProgress ?: 0}% Complete"
                            binding.resumeProgressBar.progress = data.resumeProgress ?: 0 // [cite: 67, 89]


                            data.deadlines?.let {
                                deadlinesList.clear()
                                deadlinesList.addAll(it)
                                deadlineAdapter.updateData(deadlinesList)
                            }
                        } else {
                            Snackbar.make(binding.root, data.message ?: "Failed to load dashboard data", Snackbar.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Snackbar.make(binding.root, "Error: ${response.code()} - ${response.message()}", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Snackbar.make(binding.root, "Network error: ${e.message}", Snackbar.LENGTH_LONG).show()
            } finally {

            }
        }
    }
}