package com.localqbank.library

import android.content.Context

/** Small compatibility helper used only for the user-facing AI provider status card. */
object FirebaseAppAvailability {
    fun isConfigured(context: Context): Boolean = runCatching {
        com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()
    }.getOrDefault(false)
}
