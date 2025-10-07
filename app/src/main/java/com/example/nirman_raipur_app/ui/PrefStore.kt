package com.example.nirman_raipur_app.ui

import android.content.Context

class PrefStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getString(key: String, default: String?): String? = prefs.getString(key, default)
}
