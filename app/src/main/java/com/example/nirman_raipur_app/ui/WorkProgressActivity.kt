package com.example.nirman_raipur_app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nirman_raipur_app.MainActivity
import com.example.nirman_raipur_app.R
import com.example.nirman_raipur_app.databinding.WorkProgressBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class WorkProgressActivity : AppCompatActivity() {

    private lateinit var binding: WorkProgressBinding
    private lateinit var auth: FirebaseAuth

    private lateinit var adapter: WorkListAdapter
    private var allItems: List<WorkItem> = emptyList()

    companion object {
        private const val TAG = "WorkProgressActivity"
    }

    private lateinit var photoUri: Uri
    private lateinit var photoFile: File
    private val storage = FirebaseStorage.getInstance().reference

    private val LOCATION_PERMISSION_CODE = 3001
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var lastKnownLocation: Location? = null

    private var currentPhotoWorkId: String? = null

    // ---- NEW: filter state ----
    // Keep arrays of options (populated in populateFilterDropdowns())
    private var jobTypes: List<String> = emptyList()
    private var workDepts: List<String> = emptyList()
    private var engineers: List<String> = emptyList()
    private var plans: List<String> = emptyList()
    private var agencies: List<String> = emptyList()
    private var areas: List<String> = emptyList()
    private var cities: List<String> = emptyList()
    private var wards: List<String> = emptyList()

    // selected flags for multi-choice dialogs (parallel to the above lists)
    private var jobSelected: BooleanArray = BooleanArray(0)
    private var deptSelected: BooleanArray = BooleanArray(0)
    private var engineerSelected: BooleanArray = BooleanArray(0)
    private var planSelected: BooleanArray = BooleanArray(0)
    private var agencySelected: BooleanArray = BooleanArray(0)
    private var areaSelected: BooleanArray = BooleanArray(0)
    private var citySelected: BooleanArray = BooleanArray(0)
    private var wardSelected: BooleanArray = BooleanArray(0)

    // filters map: key -> selected values
    private val filters = mutableMapOf<String, List<String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate via ViewBinding
        binding = WorkProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // init firebase
        auth = FirebaseAuth.getInstance()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // status bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.primaryDark)

        // toolbar
        setSupportActionBar(binding.toolbarWorkProgress)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarWorkProgress.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // welcome text
        val user = auth.currentUser
        binding.tvWelcome.text = "Welcome, ${user?.email ?: "Guest"} 🎉"

        // logout action
        binding.btnLogout.setOnClickListener {
            try {
                auth.signOut()
            } catch (t: Throwable) {
                Log.w(TAG, "Sign out failed", t)
            }
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }

        // RecyclerView + adapter
        binding.rvWorkList.layoutManager = LinearLayoutManager(this)
        adapter = WorkListAdapter { item ->
            // adapter already opens WorkDetailActivity on item click.
            // Additional behavior can be handled here if desired.
        }
        binding.rvWorkList.adapter = adapter

        // sample data (replace with real fetch later)
        allItems = listOf(
            WorkItem("1", "Construction work", "2024-25", "Gobra Navapara", "In Progress", "₹ 8.07 Lakh", "06/10/2025", null),
            WorkItem("2", "Road Repair", "2023-24", "Sector 5", "Completed", "₹ 1.25 Lakh", "01/09/2024", null),
            WorkItem("3", "Drainage", "2022-23", "Block A", "In Progress", "₹ 3.50 Lakh", "15/06/2025", null),
            WorkItem("4", "Park Renovation", "2024-25", "Central Park", "Pending", "₹ 5.00 Lakh", "20/08/2025", null)
        )
        submitListAndUpdateUI(allItems)

        // search filtering
        binding.etSearchGlobal.addTextChangedListener { editable ->
            val q = editable?.toString()?.trim()?.lowercase() ?: ""
            if (q.isEmpty()) {
                applyFiltersToAdapter() // show filtered by selected filters or all if none
            } else {
                // combine text search + existing filters
                val filteredByText = allItems.filter {
                    it.type.lowercase().contains(q) ||
                            it.location.lowercase().contains(q) ||
                            it.year.lowercase().contains(q) ||
                            it.status.lowercase().contains(q)
                }
                // apply selected filters on top of the text-filtered list
                applyFiltersToAdapter(baseList = filteredByText)
            }
        }

        // Populate filter lists and wire adapters
        populateFilterDropdowns()

        // 📅 Date picker setup
        val dateEditText = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_date)
        dateEditText?.apply {
            isFocusable = false
            isClickable = true

            setOnClickListener {
                // Use current date or pre-filled date as default
                val calendar = java.util.Calendar.getInstance()

                // If user already selected a date earlier, show it by default
                val existingText = text?.toString()?.trim()
                if (!existingText.isNullOrEmpty() && existingText.matches(Regex("\\d{2}-\\d{2}-\\d{4}"))) {
                    try {
                        val parts = existingText.split("-")
                        val day = parts[0].toInt()
                        val month = parts[1].toInt() - 1
                        val year = parts[2].toInt()
                        calendar.set(year, month, day)
                    } catch (_: Exception) {
                        // Ignore parse errors; fallback to current date
                    }
                }

                val year = calendar.get(java.util.Calendar.YEAR)
                val month = calendar.get(java.util.Calendar.MONTH)
                val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

                // Open DatePickerDialog
                val datePicker = android.app.DatePickerDialog(
                    this@WorkProgressActivity,
                    { _, selectedYear, selectedMonth, selectedDay ->
                        val formattedDate = String.format("%02d-%02d-%04d", selectedDay, selectedMonth + 1, selectedYear)
                        setText(formattedDate)
                    },
                    year, month, day
                )

                datePicker.show()
            }
        }



        // Setup multi-select click handlers for available AutoCompleteTextViews
        wireMultiSelectFilters()

        // filter buttons
        binding.btnApplyFilters.setOnClickListener {
            // apply filters to adapter (explicit apply button still works)
            applyFiltersToAdapter()
            Toast.makeText(this, "Filters applied", Toast.LENGTH_SHORT).show()
        }

        binding.btnResetFilters.setOnClickListener {
            resetFilters()
            Toast.makeText(this, "Filters reset", Toast.LENGTH_SHORT).show()
        }

        //  Dynamic "Entries per page" dropdown
        val defaultEntries = listOf("10", "25", "50", "100") // default values
        val dynamicEntries = loadEntriesPerPageOptions() // you’ll define this next
        val entries = if (dynamicEntries.isNotEmpty()) dynamicEntries else defaultEntries

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, entries)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.showEntriesSpinner.adapter = spinnerAdapter

    }

    // Open the camera to take a photo
    fun openCameraAndUpload(workId: String) {
        currentPhotoWorkId = workId
        // Create temporary file to store photo
        photoFile = File.createTempFile(
            "work_photo_${System.currentTimeMillis()}",
            ".jpg",
            getExternalFilesDir("Pictures")
        )

        photoUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            photoFile
        )

        // Request permission if not granted
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 200)
            return
        }

        // Launch camera intent
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoUri)
        startCamera.launch(intent)
    }

    @SuppressLint("MissingPermission")
    private val startCamera =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Photo was captured and saved to photoUri (you prepared this before launching)
                // Try to obtain last known location (if permission granted)
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    // safe call to fusedLocationClient.lastLocation
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { location: Location? ->
                            val lat = location?.latitude
                            val lng = location?.longitude

                            // Immediately update UI with coords (if we have a work id)
                            currentPhotoWorkId?.let { workId ->
                                updateWorkItemCoords(workId, lat, lng)
                            }

                            // Proceed to upload with coordinates (may be null)
                            uploadImageToFirebase(photoUri, lat, lng)
                        }
                        .addOnFailureListener { ex ->
                            Log.w(TAG, "Failed to get location: ${ex.message}", ex)
                            // Upload without coords if location fetch fails
                            uploadImageToFirebase(photoUri, null, null)
                        }
                } else {
                    // Location permission not granted -> request it and upload without coords
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        LOCATION_PERMISSION_CODE
                    )
                    uploadImageToFirebase(photoUri, null, null)
                }
            } else {
                // Camera cancelled or failed; notify user
                Toast.makeText(this, "Camera cancelled", Toast.LENGTH_SHORT).show()
            }
        }

    private fun updateWorkItemCoords(workId: String, lat: Double?, lng: Double?) {
        // find index in allItems
        val index = allItems.indexOfFirst { it.id == workId }
        if (index == -1) return

        val orig = allItems[index]
        // assuming WorkItem has latitude/longitude fields (Double?); if not, add them to the data class
        val updated = orig.copy(
            latitude = lat,
            longitude = lng
        )

        val newList = allItems.toMutableList().apply { this[index] = updated }
        allItems = newList // update backing list
        submitListAndUpdateUI(newList)
    }

    private fun uploadImageToFirebase(uri: Uri, lat: Double?, lng: Double?) {
        val fileRef = storage.child("work_photos/${System.currentTimeMillis()}.jpg")
        val uploadTask = fileRef.putFile(uri)

        uploadTask.addOnSuccessListener {
            fileRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                // If you want, update WorkItem image url + coords (overwrite or confirm)
                currentPhotoWorkId?.let { workId ->
                    val idx = allItems.indexOfFirst { it.id == workId }
                    if (idx != -1) {
                        val orig = allItems[idx]
                        val updated = orig.copy(
                            imageUrl = downloadUrl.toString(),
                            latitude = lat ?: orig.latitude,
                            longitude = lng ?: orig.longitude
                        )
                        val newList = allItems.toMutableList().apply { this[idx] = updated }
                        allItems = newList
                        submitListAndUpdateUI(newList)
                    }
                }
                Toast.makeText(this, "Uploaded ✅", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Upload failed: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission granted. If you want to automatically fetch & upload again,
                // you might re-run location + upload flow here (but careful to avoid duplicates).
                Toast.makeText(this, "Location permission granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getLastKnownLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_CODE
            )
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    lastKnownLocation = location
                    Log.d(TAG, "📍 Location: ${location.latitude}, ${location.longitude}")
                } else {
                    Log.w(TAG, "⚠️ Location unavailable")
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "❌ Failed to get location", it)
            }
    }

    /**
     * Loads entries-per-page options dynamically.
     * You can later modify this to fetch from Firebase, SharedPreferences, or server API.
     */
    private fun loadEntriesPerPageOptions(): List<String> {
        // Example 1: load from SharedPreferences
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val csv = prefs.getString("entries_per_page_list", "") ?: ""

        // Example 2: if you fetched from backend and cached it, parse it here
        // For now, assume the format is "10,25,50,100"
        val list = csv.split(",").mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }

        Log.d(TAG, "Loaded entries per page options: $list")
        return list
    }

    private fun submitListAndUpdateUI(list: List<WorkItem>) {
        adapter.submitList(list)
        binding.tvEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.tvTotalCount.text = "•  List of Work In Progress - Total: ${list.size}"
    }

    /**
     * Populate the AutoComplete lists (and save option arrays for multi-select dialogs)
     */
    private fun populateFilterDropdowns() {
        // Simple static data for demo — swap with real data source later
        jobTypes = listOf("All", "Construction work", "Restoration work", "Courtyard renovation")
        workDepts = listOf("All", "Scheduled Caste Development Authority", "Education Department", "General Administration Department", "Panchayat and Rural Development Department")
        engineers = listOf("All", "Mrs. Shraddha Kushwaha (Engineer)", "Praveen Kumar Sinha (Engineer)", "Gopi Ram Verma (Engineer)", "Pradip Sahu (Engineer)", "Tika Ram Sahu (Engineer)", "Amitesh Gupta (Engineer)", "Kamlesh Chandrakar (Engineer)", "Surya Kumar Sonkar (Engineer)", "Dinesh Bisen (Engineer)", "Dinesh Netam (Engineer)", "Vikas Nayak (Engineer)", "Prateek Singh (Engineer)", "Vikas Gupta (Engineer)", "P.L.Miri (Engineer)", "Mukund Sahu (Engineer)", "Sunita Harde (Engineer)", "Arun Kumar Banwasi (Engineer)", "Divyansh Sharma (Engineer)"
        )
        plans = listOf("All", "National Secondary Education Mission (Samagra Shiksha)", "District Mineral Trust (DMF)", "Swachh Bharat Mission Rural Scheme", "Health Services", "CSR Item", "Women and Child Development Department (Vatsalya Scheme)", "MLA Fund", "Mahatari Sadan Scheme", "Panchayat and State Rural Development Institute, Nimora", "P.M.Shri", "Scheduled Caste Development Authority (Tribal Development)", "Chief Minister's Announcement Item"
        )
        agencies = listOf("All", "Agency 1", "Agency 2")
        areas = listOf("All", "Raipur", "Arang", "Abhanpur", "Tilda", "Dharsiwa"
        )
        cities = listOf("All", "Raipur", "Abhanpur")
        wards = listOf("All", "Fundhar", "Rewa", "Corac", "Bhandarpuri", "Ganoud", "Bhansoj", "Lakhauli", "Bhothli", "Amsena", "Arang", "Nemora", "Gatapar", "Bendri", "Gobra Navapara", "Abhanpur", "Khorpa", "Mundra", "Sarora", "Basil", "Budyonny", "Saragaon", "Sarakh", "Dharsiwa", "Kandul", "Kathadih", "Datrenga", "Chandkhuri", "Tekari", "Swamp Suture", "Mana Basti", "Dondekala", "Parastarai", "Temari", "Serikhedi", "Dehpara Giraud", "Nevra", "Kharora", "Sirve", "Birgaon", "Ratakat", "Farfraud", "Benideh", "Deori", "Majitha", "Acholi", "Ceja", "Gukhera", "Ranisagar", "Chaprid", "Kosrangi", "Chhatauna", "Gullu", "Nardaha", "Raipur", "Profiteer", "Khamhardih", "Adarsh Nagar Raipur", "Zora", "Akolikala (Bhau)", "Godhi", "Buffalo", "Rakhi", "Bana", "Parsada (Umaria)", "Rico", "Kusmunda"
        )

        // initialize boolean selection arrays
        jobSelected = BooleanArray(jobTypes.size)
        deptSelected = BooleanArray(workDepts.size)
        engineerSelected = BooleanArray(engineers.size)
        planSelected = BooleanArray(plans.size)
        agencySelected = BooleanArray(agencies.size)
        areaSelected = BooleanArray(areas.size)
        citySelected = BooleanArray(cities.size)
        wardSelected = BooleanArray(wards.size)

        // Fill the simple AutoCompleteTextView adapters (so dropdown arrow still shows)
        safeSetAutoComplete("jobTypeSpinner", jobTypes)
        safeSetAutoComplete("workDepartmentSpinner", workDepts)
        safeSetAutoComplete("engineerSpinner", engineers)
        safeSetAutoComplete("planSpinner", plans)
        safeSetAutoComplete("workAgencySpinner", agencies)
        safeSetAutoComplete("areaSpinner", areas)
        safeSetAutoComplete("citySpinner", cities)
        safeSetAutoComplete("wardSpinner", wards)
    }

    /**
     * Wire the AutoCompleteTextViews to open a multi-choice dialog when clicked.
     * Uses the same safe lookup pattern.
     */
    private fun wireMultiSelectFilters() {
        safeSetMultiSelect("jobTypeSpinner", jobTypes, jobSelected) { chosen ->
            filters["jobType"] = chosen
            applyFiltersToAdapter()
        }
        safeSetMultiSelect("workDepartmentSpinner", workDepts, deptSelected) { chosen ->
            filters["workDepartment"] = chosen
            applyFiltersToAdapter()
        }
        safeSetMultiSelect("engineerSpinner", engineers, engineerSelected) { chosen ->
            filters["engineer"] = chosen
            applyFiltersToAdapter()
        }
        safeSetMultiSelect("planSpinner", plans, planSelected) { chosen ->
            filters["plan"] = chosen
            applyFiltersToAdapter()
        }
        safeSetMultiSelect("workAgencySpinner", agencies, agencySelected) { chosen ->
            filters["agency"] = chosen
            applyFiltersToAdapter()
        }
        safeSetMultiSelect("areaSpinner", areas, areaSelected) { chosen ->
            filters["area"] = chosen
            applyFiltersToAdapter()
        }
        safeSetMultiSelect("citySpinner", cities, citySelected) { chosen ->
            filters["city"] = chosen
            applyFiltersToAdapter()
        }
        safeSetMultiSelect("wardSpinner", wards, wardSelected) { chosen ->
            filters["ward"] = chosen
            applyFiltersToAdapter()
        }
    }

    /**
     * Helper: find AutoCompleteTextView by idName and attach a click listener which shows a multi-choice dialog.
     */
    private fun safeSetMultiSelect(idName: String, items: List<String>, selectedArr: BooleanArray, onConfirmed: (List<String>) -> Unit) {
        val resId = resources.getIdentifier(idName, "id", packageName)
        if (resId == 0) {
            Log.d(TAG, "safeSetMultiSelect: id not found -> $idName")
            return
        }
        val view = findViewById<AutoCompleteTextView?>(resId)
        if (view == null) {
            Log.d(TAG, "safeSetMultiSelect: view lookup returned null for id -> $idName")
            return
        }

        // Make it non-focusable to avoid keyboard and keep it clickable
        view.isFocusable = false
        view.isClickable = true

        // Show a Material multi-choice dialog
        view.setOnClickListener {
            showMultiChoiceDialog(
                title = view.hint?.toString() ?: "-- Select --",
                options = items.toTypedArray(),
                initiallySelected = selectedArr
            ) { chosenList ->
                // Update the AutoCompleteTextView display (comma-separated) and update boolean selection array
                view.setText(if (chosenList.isEmpty()) "" else chosenList.joinToString(", "), false)
                items.forEachIndexed { i, opt -> selectedArr[i] = chosenList.contains(opt) }
                // callback to update filters & adapter
                onConfirmed(chosenList)
            }
        }
    }

    /**
     * Show a Material multi-choice dialog (checkbox list)
     */
    private fun showMultiChoiceDialog(
        title: String,
        options: Array<String>,
        initiallySelected: BooleanArray? = null,
        onSelectionConfirmed: (selected: List<String>) -> Unit
    ) {
        val selected = BooleanArray(options.size) { i ->
            initiallySelected?.getOrNull(i) ?: false
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMultiChoiceItems(options, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                val chosen = options
                    .indices
                    .filter { selected[it] }
                    .map { options[it] }
                onSelectionConfirmed(chosen)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun safeSetAutoComplete(idName: String, items: List<String>) {
        val resId = resources.getIdentifier(idName, "id", packageName)
        if (resId == 0) {
            // View with that id not present in layout; silent skip (or you can log)
            Log.d(TAG, "safeSetAutoComplete: id not found -> $idName")
            return
        }
        val view = findViewById<AutoCompleteTextView?>(resId)
        if (view != null) {
            val a = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
            view.setAdapter(a)
            view.setText(items.firstOrNull() ?: "", false)
        } else {
            Log.d(TAG, "safeSetAutoComplete: view lookup returned null for id -> $idName")
        }
    }

    private fun setAutoComplete(target: AutoCompleteTextView, items: List<String>) {
        val a = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        target.setAdapter(a)
        target.setText(items.first(), false)
    }

    private fun resetFilters() {
        // reset only if the views exist; safeSetAutoComplete will silently skip non-existent ids
        populateFilterDropdowns() // reset lists and selections to defaults (All)
        // clear each AutoCompleteTextView text if present
        safeClearText("jobTypeSpinner")
        safeClearText("workDepartmentSpinner")
        safeClearText("engineerSpinner")
        safeClearText("planSpinner")
        safeClearText("workAgencySpinner")
        safeClearText("areaSpinner")
        safeClearText("citySpinner")
        safeClearText("wardSpinner")

        // clear date field if present
        binding.root.findViewById<TextInputEditText?>(R.id.et_date)
            ?.setText("")

        // clear filters map and re-apply (show all)
        filters.clear()
        applyFiltersToAdapter()
    }

    private fun safeClearText(idName: String) {
        val resId = resources.getIdentifier(idName, "id", packageName)
        if (resId == 0) return
        val view = findViewById<AutoCompleteTextView?>(resId)
        view?.setText("", false)
    }

    /**
     * Apply the currently selected filters to the adapter.
     * If baseList is provided, filter that list; otherwise start from allItems.
     */
    private fun applyFiltersToAdapter(baseList: List<WorkItem>? = null) {
        val listToFilter = baseList ?: allItems

        // If no filters selected -> show base list
        if (filters.values.all { it.isEmpty() }) {
            submitListAndUpdateUI(listToFilter)
            return
        }

        val filtered = listToFilter.filter { item ->
            // Each filter must match OR be empty. Adapt item field names to your WorkItem.
            val jobOk = filters["jobType"].let { sel ->
                sel == null || sel.isEmpty() || sel.contains(item.type) || sel.contains("All")
            } ?: true

            val deptOk = filters["workDepartment"].let { sel ->
                sel == null || sel.isEmpty() || sel.contains(item.workDepartment ?: "") || sel.contains("All")
            } ?: true

            val engineerOk = filters["engineer"].let { sel ->
                sel == null || sel.isEmpty() || sel.contains(item.engineer ?: "") || sel.contains("All")
            } ?: true

            val planOk = filters["plan"].let { sel ->
                sel == null || sel.isEmpty() || sel.contains(item.plan ?: "") || sel.contains("All")
            } ?: true

            val agencyOk = filters["agency"].let { sel ->
                sel == null || sel.isEmpty() || sel.contains(item.workAgency ?: "") || sel.contains("All")
            } ?: true

            val areaOk = filters["area"].let { sel ->
                sel == null || sel.isEmpty() || sel.contains(item.area ?: "") || sel.contains("All")
            } ?: true

            val cityOk = filters["city"].let { sel ->
                sel == null || sel.isEmpty() || sel.contains(item.city ?: "") || sel.contains("All")
            } ?: true

            val wardOk = filters["ward"].let { sel ->
                sel == null || sel.isEmpty() || sel.contains(item.ward ?: "") || sel.contains("All")
            } ?: true

            jobOk && deptOk && engineerOk && planOk && agencyOk && areaOk && cityOk && wardOk
        }

        submitListAndUpdateUI(filtered)
    }

}
