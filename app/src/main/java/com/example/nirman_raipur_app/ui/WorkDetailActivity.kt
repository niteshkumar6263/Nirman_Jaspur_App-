package com.example.nirman_raipur_app.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nirman_raipur_app.R
import com.example.nirman_raipur_app.databinding.ActivityWorkDetailBinding
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

class WorkDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_WORK_ID = "extra_work_id"
        private const val TAG = "WorkDetailActivity"
    }

    private lateinit var binding: ActivityWorkDetailBinding
    private lateinit var prefs: PrefStore
    private lateinit var currentWorkId: String
    private val gson = Gson()

    private val selectedDocUris = mutableListOf<String>()
    private val selectedImageUris = mutableListOf<String>()

    private lateinit var instAdapter: InstallmentAdapter

    private val pickDocLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selectedDocUris.add(it.toString())
            updateSelectedDocsUi()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selectedImageUris.add(it.toString())
            updateSelectedImagesUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefStore(this)

        currentWorkId = intent.getStringExtra(EXTRA_WORK_ID) ?: run {
            Toast.makeText(this, "No work provided", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Work Progress"

        val statuses = listOf("-- Select job status --", "Not Started", "In Progress", "Completed", "Hold")
        val spAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, statuses)
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStatus.adapter = spAdapter

        instAdapter = InstallmentAdapter(mutableListOf(), removeCallback = { /* nothing extra */ }) { pos ->
            // open date picker for this position
            pickDateForInstallment(pos)
        }
        binding.rvInstallments.layoutManager = LinearLayoutManager(this)
        binding.rvInstallments.adapter = instAdapter

        if (instAdapter.itemCount == 0) instAdapter.add(Installment())

        binding.btnSelectDoc.setOnClickListener {
            pickDocLauncher.launch(arrayOf("*/*"))
        }
        binding.btnSelectImages.setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        binding.btnAddInstallment.setOnClickListener {
            instAdapter.add(Installment())
        }

        binding.btnSave.setOnClickListener {
            saveProgress()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }

        loadExistingProgress()
    }

    private fun updateSelectedDocsUi() {
        binding.tvSelectedDocs.text = if (selectedDocUris.isEmpty()) "No file selected" else selectedDocUris.joinToString("\n") { uriStr -> filenameForUri(Uri.parse(uriStr)) }
    }

    private fun updateSelectedImagesUi() {
        binding.tvSelectedImages.text = if (selectedImageUris.isEmpty()) "No image selected" else selectedImageUris.joinToString("\n") { uriStr -> filenameForUri(Uri.parse(uriStr)) }
    }

    private fun filenameForUri(uri: Uri): String {
        var name = uri.lastPathSegment ?: uri.toString()
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun loadExistingProgress() {
        val json = prefs.getString("progress_$currentWorkId", null)
        if (json.isNullOrBlank()) return
        try {
            val saved = gson.fromJson(json, SavedProgress::class.java)
            val statusIndex = (binding.spinnerStatus.adapter as ArrayAdapter<String>).getPosition(saved.status)
            if (statusIndex >= 0) binding.spinnerStatus.setSelection(statusIndex)
            binding.etProgressReport.setText(saved.progressReport)
            binding.etSanctioned.setText(saved.sanctioned)
            binding.etReleased.setText(saved.released)
            binding.etBalance.setText(saved.balance)
            binding.etMbStage.setText(saved.mbStage)
            binding.etExpenditure.setText(saved.expenditure)

            selectedDocUris.clear(); selectedDocUris.addAll(saved.docs)
            selectedImageUris.clear(); selectedImageUris.addAll(saved.images)
            updateSelectedDocsUi(); updateSelectedImagesUi()

            instAdapter.replaceAll(saved.installments.toMutableList())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse saved progress: ${e.message}")
        }
    }

    private fun saveProgress() {
        val status = binding.spinnerStatus.selectedItem as String
        if (status == "-- Select job status --") {
            Toast.makeText(this, "Please choose a status", Toast.LENGTH_SHORT).show()
            return
        }
        val saved = SavedProgress(
            status = status,
            progressReport = binding.etProgressReport.text.toString(),
            sanctioned = binding.etSanctioned.text.toString(),
            released = binding.etReleased.text.toString(),
            balance = binding.etBalance.text.toString(),
            mbStage = binding.etMbStage.text.toString(),
            expenditure = binding.etExpenditure.text.toString(),
            docs = selectedDocUris.toList(),
            images = selectedImageUris.toList(),
            installments = instAdapter.items.toList()
        )
        val json = gson.toJson(saved)
        prefs.putString("progress_$currentWorkId", json)
        Toast.makeText(this, "Progress saved", Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // date picker for installments
    private fun pickDateForInstallment(position: Int) {
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        val existing = instAdapter.items.getOrNull(position)?.date
        if (!existing.isNullOrBlank()) {
            try { cal.time = sdf.parse(existing) } catch (_: Exception) {}
        }
        val listener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            val c = Calendar.getInstance()
            c.set(year, month, day)
            val formatted = sdf.format(c.time)
            if (position in instAdapter.items.indices) {
                instAdapter.items[position].date = formatted
                instAdapter.notifyItemChanged(position)
            }
        }
        DatePickerDialog(this, listener, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
}

data class SavedProgress(
    val status: String = "",
    val progressReport: String = "",
    val sanctioned: String = "",
    val released: String = "",
    val balance: String = "",
    val mbStage: String = "",
    val expenditure: String = "",
    val docs: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val installments: List<Installment> = emptyList()
)
