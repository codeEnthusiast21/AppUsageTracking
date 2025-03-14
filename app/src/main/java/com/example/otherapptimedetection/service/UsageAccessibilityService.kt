package com.example.otherapptimedetection.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore

class UsageAccessibilityService : AccessibilityService() {

    private val db = FirebaseFirestore.getInstance()
    private var lastForegroundApp: String? = null
    private var lastStartTime: Long = 0
    private val appUsageTimes = HashMap<String, Long>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            checkIfAppBlocked(packageName)

            if (packageName != lastForegroundApp) {
                val currentTime = System.currentTimeMillis()

                lastForegroundApp?.let {
                    val duration = currentTime - lastStartTime
                    appUsageTimes[it] = (appUsageTimes[it] ?: 0) + duration
                    updateFirestore(it, duration)
                }

                lastForegroundApp = packageName
                lastStartTime = currentTime
            }
        }
    }

    override fun onInterrupt() {
        Log.d("UsageService", "Service Interrupted")
    }

    private fun updateFirestore(packageName: String, duration: Long) {
        val appRef = db.collection("app_usage").document(packageName)

        appRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val currentTimeUsed = document.getLong("totalTimeUsed") ?: 0
                val newTotalTime = currentTimeUsed + duration

                appRef.update(
                    "totalTimeUsed", newTotalTime,
                    "lastUpdated", System.currentTimeMillis()
                ).addOnSuccessListener {
                    Log.d("Firestore", "Updated $packageName usage time: $newTotalTime ms")
                }.addOnFailureListener { e ->
                    Log.e("Firestore", "Error updating Firestore: ${e.message}")
                }
            } else {
                // Create a new document if it doesn't exist
                val appData = hashMapOf(
                    "packageName" to packageName,
                    "totalTimeUsed" to duration,
                    "lastUpdated" to System.currentTimeMillis()
                )

                appRef.set(appData)
                    .addOnSuccessListener {
                        Log.d("Firestore", "Added new app usage entry for $packageName")
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error adding Firestore document: ${e.message}")
                    }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("UsageAccessibilityService", "Service Connected")
        lastStartTime = System.currentTimeMillis()
    }

    private fun checkIfAppBlocked(packageName: String) {
        Log.d("UsageAccessibilityService", "Checking if app $packageName is blocked")
        db.collection("blocked_apps")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val storedAppName = document.getString("appName") ?: continue
                    val storedPackageName = document.getString("packageName") ?: continue

                    // Log the comparison values
                    Log.d(
                        "UsageAccessibilityService",
                        "Comparing: stored appName=$storedAppName, stored packageName=$storedPackageName with current=$packageName"
                    )

                    if (packageName.contains(storedAppName, ignoreCase = true) ||
                        packageName.contains(storedPackageName, ignoreCase = true)
                    ) {

                        Log.d("UsageAccessibilityService", "App $packageName is blocked")
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)

                        Toast.makeText(
                            applicationContext,
                            "This app is blocked",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addOnSuccessListener
                    }
                }
                Log.d("UsageAccessibilityService", "App $packageName is not blocked")
            }
            .addOnFailureListener { e ->
                Log.e("UsageAccessibilityService", "Error checking if app is blocked: ${e.message}")
            }
    }
}