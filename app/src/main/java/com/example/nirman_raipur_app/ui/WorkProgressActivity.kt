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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate via ViewBinding
        binding = WorkProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // init firebase
        auth = FirebaseAuth.getInstance()

//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
//        getLastKnownLocation()
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
                submitListAndUpdateUI(allItems)
            } else {
                val filtered = allItems.filter {
                    it.type.lowercase().contains(q) ||
                            it.location.lowercase().contains(q) ||
                            it.year.lowercase().contains(q) ||
                            it.status.lowercase().contains(q)
                }
                submitListAndUpdateUI(filtered)
            }
        }

        // Populate simple dropdowns for filters (AutoCompleteTextView)
        populateFilterDropdowns()

        // filter buttons
        binding.btnApplyFilters.setOnClickListener {
            Toast.makeText(this, "Apply filters (TODO)", Toast.LENGTH_SHORT).show()
        }

        binding.btnResetFilters.setOnClickListener {
            resetFilters()
            Toast.makeText(this, "Filters reset", Toast.LENGTH_SHORT).show()
        }

//        // FAB
//        binding.fabAddWork.setOnClickListener {
//            Toast.makeText(this, "Add Work (TODO)", Toast.LENGTH_SHORT).show()
//        }

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

    // Handle camera result
//    private val startCamera =
//        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == Activity.RESULT_OK) {
//                uploadImageToFirebase(photoUri, lastKnownLocation?.latitude, lastKnownLocation?.longitude)
//            } else {
//                Toast.makeText(this, "Camera cancelled", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//    // Upload to Firebase Storage
//    private fun uploadImageToFirebase(uri: Uri, lat: Double?, lng: Double?) {
//        // defensive: ensure file exists (if using file:// or FileProvider URIs)
//        val f = File(uri.path ?: "")
//        if (!f.exists()) {
//            Log.e(TAG, "uploadImageToFirebase: file does not exist at uri: $uri (path ${uri.path})")
//            Toast.makeText(this, "Photo file missing, cannot upload", Toast.LENGTH_LONG).show()
//            return
//        }
//
//        // If you need to target a specific bucket, do: FirebaseStorage.getInstance("gs://your-bucket.appspot.com").reference
//        val fileRef = storage.child("work_photos/${System.currentTimeMillis()}.jpg")
//
//        Log.d(TAG, "Starting upload to storage path: ${fileRef.path}")
//
//        val uploadTask = fileRef.putFile(uri)
//
//        // Use continueWithTask to ensure we get downloadUrl only after successful upload
//        uploadTask.continueWithTask { task ->
//            if (!task.isSuccessful) {
//                task.exception?.let { throw it } // propagate error to onFailureListener below
//            }
//            // now request download URL
//            fileRef.downloadUrl
//        }.addOnCompleteListener { task ->
//            if (task.isSuccessful) {
//                val downloadUrl = task.result
//                Log.d(TAG, "Upload succeeded. downloadUrl=$downloadUrl")
//
//                // show toast and log coordinates
//                Toast.makeText(this, "Uploaded ✅", Toast.LENGTH_SHORT).show()
//                Log.d(TAG, "Uploaded photo location -> Lat: $lat, Lng: $lng")
//
//                // Update UI / items safely on main thread
//                runOnUiThread {
//                    // (example) update first item — adapt to your app logic
//                    if (allItems.isNotEmpty()) {
//                        val updatedItem = allItems[0].copy(imageUrl = downloadUrl.toString(), latitude = lat, longitude = lng)
//                        val newList = allItems.toMutableList().apply { this[0] = updatedItem }
//                        submitListAndUpdateUI(newList)
//                    }
//                }
//            } else {
//                Log.e(TAG, "Upload failed", task.exception)
//                Toast.makeText(this, "Upload failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
//
//                // optional: check detailed error type
//                val ex = task.exception
//                if (ex != null) {
//                    Log.e(TAG, "Upload exception: ${ex::class.java.name} : ${ex.message}")
//                }
//            }
//        }
//    }
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

    private fun populateFilterDropdowns() {
        // Simple static data for demo — swap with real data source later
        val jobTypes = listOf("All", "Construction", "Road", "Drainage", "Park")
        val workDepts = listOf("All", "PHE", "PWD", "Municipal")
        val engineers = listOf("All", "Eng. Sharma", "Eng. Verma", "Eng. Patel")
        val plans = listOf("All", "Plan A", "Plan B")
        val agencies = listOf("All", "Agency 1", "Agency 2")
        val areas = listOf("All", "Area 1", "Area 2")
        val cities = listOf("All", "Raipur", "Abhanpur")
        val wards = listOf("All", "Ward 1", "Ward 2")

        // Use safe lookup by id-name so this file compiles even when inner AutoCompleteTextViews aren't present in layout.
        safeSetAutoComplete("jobTypeSpinner", jobTypes)
        safeSetAutoComplete("workDepartmentSpinner", workDepts)
        safeSetAutoComplete("engineerSpinner", engineers)
        safeSetAutoComplete("planSpinner", plans)
        safeSetAutoComplete("workAgencySpinner", agencies)
        safeSetAutoComplete("areaSpinner", areas)
        safeSetAutoComplete("citySpinner", cities)
        safeSetAutoComplete("wardSpinner", wards)
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
            view.setText(items.first(), false)
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
        safeSetAutoComplete(idName = "jobTypeSpinner", items = listOf("All", "Construction", "Road", "Drainage", "Park"))
        safeSetAutoComplete(idName = "workDepartmentSpinner", items = listOf("All", "PHE", "PWD", "Municipal"))
        // clear date field if present
        binding.root.findViewById<com.google.android.material.textfield.TextInputEditText?>(R.id.et_date)
            ?.setText("")
    }

}
