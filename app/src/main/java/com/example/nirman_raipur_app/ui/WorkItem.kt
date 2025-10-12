package com.example.nirman_raipur_app.ui

data class WorkItem(
    val id: String,
    val type: String,
    val year: String,
    val location: String,
    val status: String,
    val amountFormatted: String,
    val lastModified: String,
    val imageUrl: String? = null,
    val latitude: Double? = null,     // existing
    val longitude: Double? = null,    // existing

    // 🔽 New optional fields for filters (added safely)
    val workDepartment: String? = null,
    val engineer: String? = null,
    val plan: String? = null,
    val workAgency: String? = null,
    val area: String? = null,
    val city: String? = null,
    val ward: String? = null
)
