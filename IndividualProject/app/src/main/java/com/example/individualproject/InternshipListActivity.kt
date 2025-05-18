package com.example.individualproject

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.individualproject.databinding.ActivityInternshipListBinding
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import android.view.MenuItem
import android.content.Intent
import android.util.Log

class InternshipListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInternshipListBinding
    private lateinit var internshipAdapter: InternshipAdapter
    private var internshipList = mutableListOf<Internship>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInternshipListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarInternships)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        supportActionBar?.title = "Internships"

        setupInternshipsRecyclerView()
        setupSearchView()
        setupFilterChips()

        binding.refreshFab.setOnClickListener {
            fetchInternships(binding.searchView.query.toString().trim(), getSelectedFilters())
        }


        fetchInternships(null, getSelectedFilters())
    }

    private fun navigateToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)

        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (item.itemId == android.R.id.home) {
            navigateToDashboard()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupInternshipsRecyclerView() {
        internshipAdapter = InternshipAdapter(
            internshipList,
            onItemClick = { internship ->

                Toast.makeText(this, "Clicked on: ${internship.title}", Toast.LENGTH_SHORT).show()
            },
            onApplyClick = { internship ->
                applyForInternship(internship)
            },
            onBookmarkClick = { internship ->
                handleBookmark(internship)
            }
        )
        binding.internshipsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@InternshipListActivity)
            adapter = internshipAdapter
        }
        println("RecyclerView for internships setup")
    }

    private fun fetchInternships(query: String?, filters: List<String>?) {
        println("Fetching internships... Query: '$query', Filters: $filters")


        lifecycleScope.launch {
            try {
                val effectiveQuery = query?.ifEmpty { null }
                val effectiveFilters = filters?.joinToString(",")?.ifEmpty { null }

                Log.d("InternshipList", "Attempting to fetch with query: '$effectiveQuery', filters: '$effectiveFilters'")

                val response = RetrofitClient.instance.getInternships(
                    searchQuery = effectiveQuery,
                    filters = effectiveFilters
                )

                if (response.isSuccessful) {
                    response.body()?.let { data ->
                        if (!data.error) {
                            internshipList.clear()
                            data.internships?.let { internships ->

                                internshipList.addAll(internships)
                            }
                            internshipAdapter.updateData(internshipList)
                        } else {
                            Snackbar.make(binding.root, data.message ?: "Failed to load internships", Snackbar.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Snackbar.make(binding.root, "Error: ${response.code()}", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Network Error: ${e.message}", Snackbar.LENGTH_LONG).show()
            } finally {

            }
        }
    }

    private fun applyForInternship(internship: Internship) {
        lifecycleScope.launch {
            try {

                val response = RetrofitClient.instance.applyForInternship(internship.id)

                if (response.isSuccessful && !response.body()!!.error) {
                    Snackbar.make(binding.root, response.body()!!.message, Snackbar.LENGTH_LONG).show()

                } else {
                    Snackbar.make(binding.root, response.body()?.message ?: "Application failed", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error applying: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun handleBookmark(internship: Internship) {

        lifecycleScope.launch {
            try {
                val response = if (internship.isBookmarked) {
                    RetrofitClient.instance.addBookmark(internship.id)
                } else {
                    RetrofitClient.instance.removeBookmark(internship.id)
                }
                if (response.isSuccessful && !response.body()!!.error) {
                    Toast.makeText(this@InternshipListActivity,
                        if(internship.isBookmarked) "Bookmarked: ${internship.title}" else "Bookmark removed: ${internship.title}",
                        Toast.LENGTH_SHORT).show()
                } else {

                    internship.isBookmarked = !internship.isBookmarked
                    internshipAdapter.notifyItemChanged(internshipList.indexOf(internship))
                    Toast.makeText(this@InternshipListActivity, "Bookmark action failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                internship.isBookmarked = !internship.isBookmarked
                internshipAdapter.notifyItemChanged(internshipList.indexOf(internship))
                Toast.makeText(this@InternshipListActivity, "Error bookmarking: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                fetchInternships(query?.trim(), getSelectedFilters())
                binding.searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {

                return true
            }
        })
    }

    private fun setupFilterChips() {
        val chipGroup = binding.filterChipGroup
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip
            chip?.setOnCheckedChangeListener { _, _ ->
                fetchInternships(binding.searchView.query.toString().trim(), getSelectedFilters())
            }
        }
    }

    private fun getSelectedFilters(): List<String> {
        val selectedFilters = mutableListOf<String>()
        if (binding.chipTech.isChecked) selectedFilters.add(binding.chipTech.text.toString())
        if (binding.chipDesign.isChecked) selectedFilters.add(binding.chipDesign.text.toString())
        if (binding.chipRemote.isChecked) selectedFilters.add(binding.chipRemote.text.toString())
        return selectedFilters
    }
}