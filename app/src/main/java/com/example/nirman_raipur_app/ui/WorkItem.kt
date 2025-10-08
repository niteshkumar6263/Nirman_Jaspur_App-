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
    val latitude: Double? = null,     // <-- added
    val longitude: Double? = null     // <-- added
)
