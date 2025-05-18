package com.example.individualproject

import android.R
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.individualproject.databinding.ActivityPortfolioBinding
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import java.io.File
import java.io.FileOutputStream


class PortfolioActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPortfolioBinding
    private val portfolioList = mutableListOf<PortfolioItem>()
    private lateinit var portfolioAdapter: PortfolioAdapter


    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d("PortfolioActivity", "File selected: $uri")
                uploadFile(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPortfolioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarPortfolio)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        setupPortfolioRecyclerView()
        loadPortfolioItems()


        binding.fabAddPortfolio.setOnClickListener {
            Log.d("Upload", "Upload button clicked")
            openFilePicker()
        }


        binding.fabSharePortfolio.setOnClickListener { sharePortfolio() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.home) {
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

    private fun setupPortfolioRecyclerView() {

        portfolioAdapter = PortfolioAdapter(portfolioList) { selectedItem ->

            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(selectedItem.fileUrl))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("PortfolioActivity", "Error opening file URL: ${selectedItem.fileUrl}", e)
            }
        }
        binding.portfolioRecyclerView.apply {
            layoutManager = GridLayoutManager(this@PortfolioActivity, 2)
            adapter = portfolioAdapter
        }
        updateEmptyStateVisibility()
    }

    private fun loadPortfolioItems() {
        binding.portfolioProgressBar.visibility = View.VISIBLE
        binding.textViewEmptyPortfolio.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.getPortfolioItems()
                if (response.isSuccessful && response.body() != null) {
                    val portfolioResponse = response.body()!!
                    if (!portfolioResponse.error) {
                        portfolioList.clear()
                        portfolioResponse.portfolioItems?.let { itemsFromServer ->
                            portfolioList.addAll(itemsFromServer)
                        }
                        portfolioAdapter.notifyDataSetChanged()
                    } else {
                        Toast.makeText(this@PortfolioActivity, portfolioResponse.message ?: "Failed to load portfolio items", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e("PortfolioActivity", "Failed to load portfolio items: ${response.code()} - $errorBody")
                    Toast.makeText(this@PortfolioActivity, "Error: $errorBody", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("PortfolioActivity", "Exception loading portfolio items: ${e.message}", e)
                Toast.makeText(this@PortfolioActivity, "Network error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                binding.portfolioProgressBar.visibility = View.GONE
                updateEmptyStateVisibility()
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/png", "image/jpeg", "image/jpg"))
        }
        try {
            filePickerLauncher.launch(intent)
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, "No file picker app found.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun getFileName(uri: Uri): String {
        var name = "temp_upload_file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    val displayName = cursor.getString(nameIndex)
                    if (displayName != null) {
                        name = displayName
                    }
                }
            }
        }

        return name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
    }

    private fun prepareFilePart(fileUri: Uri): MultipartBody.Part? {
        var tempFileForUpload: File? = null
        return try {
            val inputStream = contentResolver.openInputStream(fileUri)
            if (inputStream == null) {
                Toast.makeText(this, "Failed to open file stream.", Toast.LENGTH_SHORT).show()
                return null
            }

            val originalFileName = getFileName(fileUri)


            tempFileForUpload = File(cacheDir, "upload_temp_${System.currentTimeMillis()}_${originalFileName}")

            val outputStream = FileOutputStream(tempFileForUpload)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            if (!tempFileForUpload.exists() || tempFileForUpload.length() == 0L) {
                Log.e("PortfolioActivity", "Temporary file creation failed or file is empty.")
                Toast.makeText(this, "Failed to prepare file for upload.", Toast.LENGTH_SHORT).show()
                tempFileForUpload.delete()
                return null
            }

            val mediaTypeString = contentResolver.getType(fileUri) ?: "application/octet-stream"
            Log.d("PortfolioActivity", "Preparing file: ${tempFileForUpload.name}, Type: $mediaTypeString, Size: ${tempFileForUpload.length()}")



            val requestFile = ProgressRequestBody(
                tempFileForUpload,
                mediaTypeString,
                object : ProgressRequestBody.UploadCallbacks {
                    override fun onProgressUpdate(percentage: Int) {
                        Log.d("UploadProgress", "Progress: $percentage%")
                        binding.portfolioProgressBar.progress = percentage
                        binding.portfolioProgressBar.visibility = View.VISIBLE
                    }

                    override fun onError() {
                        Log.e("UploadProgress", "Upload Error in ProgressRequestBody")
                        binding.portfolioProgressBar.visibility = View.GONE
                        Toast.makeText(this@PortfolioActivity, "Upload failed during data transfer.", Toast.LENGTH_SHORT).show()
                    }

                    override fun onFinish() {
                        Log.d("UploadProgress", "Upload Finished by ProgressRequestBody")

                    }
                }
            )


            MultipartBody.Part.createFormData("portfolio_file", originalFileName, requestFile)

        } catch (e: Exception) {
            Log.e("PortfolioActivity", "Error preparing file part: ${e.message}", e)
            Toast.makeText(this, "Error preparing file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            tempFileForUpload?.delete() // Clean up temp file on error
            null
        }

    }


    // Inside PortfolioActivity.kt

    private fun uploadFile(fileUri: Uri) {
        val filePart = prepareFilePart(fileUri)

        if (filePart == null) {
            Toast.makeText(this, "Could not prepare file for upload.", Toast.LENGTH_SHORT).show()
            binding.portfolioProgressBar.visibility = View.GONE
            return
        }


        binding.portfolioProgressBar.progress = 0
        binding.portfolioProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {

                val response = RetrofitClient.instance.uploadPortfolioFile(filePart)
                if (response.isSuccessful && response.body() != null && !response.body()!!.error) {
                    val uploadResponse = response.body()!!
                    Toast.makeText(this@PortfolioActivity, uploadResponse.message ?: "File uploaded", Toast.LENGTH_LONG).show()
                    Log.d("PortfolioActivity", "Upload successful: ${uploadResponse.filename}, Path: ${uploadResponse.path}")

                    val newPortfolioItem = PortfolioItem(
                        id = (uploadResponse.portfolio_item_id ?: 0).toString(),
                        userId = 0,
                        fileName = uploadResponse.filename ?: "Unknown Filename",

                        fileUrl = uploadResponse.file_url ?: uploadResponse.path ?: "",
                        fileType = uploadResponse.filename?.substringAfterLast(
                            '.',
                            null.toString()
                        ),
                        uploadDate = System.currentTimeMillis().toString(),
                        thumbnailUrl = null,
                    )
                    portfolioAdapter.addPortfolioItem(newPortfolioItem)

                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error from server"
                    Log.e("PortfolioActivity", "Upload failed: ${response.code()} - $errorBody")
                    Toast.makeText(this@PortfolioActivity, "Upload failed: $errorBody", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("PortfolioActivity", "Exception during upload: ${e.message}", e)
                Toast.makeText(this@PortfolioActivity, "Upload error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                binding.portfolioProgressBar.visibility = View.GONE

                File(cacheDir, "upload_temp_*").listFiles()?.forEach {
                    if (it.name.startsWith("upload_temp_")) it.delete()
                }
            }
        }
    }

    private fun updateEmptyStateVisibility() {
        if (portfolioList.isEmpty()) {
            binding.textViewEmptyPortfolio.visibility = View.VISIBLE
            binding.portfolioRecyclerView.visibility = View.GONE
        } else {
            binding.textViewEmptyPortfolio.visibility = View.GONE
            binding.portfolioRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun sharePortfolio() {

        val portfolioSummary = portfolioList.joinToString(separator = "\n") { "- ${it.fileName}" }
        val shareText = """
            Check out my Career Portfolio via Smart Portfolio Tracker!
            My Projects/Certificates:
            $portfolioSummary

            [Link to my online portfolio or app placeholder] 
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "My Career Portfolio")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Portfolio via"))
    }
}