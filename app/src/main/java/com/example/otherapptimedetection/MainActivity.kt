package com.example.otherapptimedetection

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.otherapptimedetection.databinding.ActivityMainBinding
import com.example.otherapptimedetection.service.UsageAccessibilityService
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var allApps: List<AppInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPermissionButton()
        setupSpinner()
        setupBlockButton()
        loadBlockdAppps()
        loadTimeData()
    }

    private fun setupPermissionButton() {
        binding.permissionForTracking.setOnClickListener {
            if (!isPermission()) {
                requestAccessibilityPermission()
            } else {
                Toast.makeText(this, "Tracking already started", Toast.LENGTH_SHORT).show()
                updateBtnUI()
            }
        }
    }

    private fun setupSpinner() {
        allApps = getInstalledApps()
        if (allApps.isEmpty()) {
            Toast.makeText(this, "No apps found", Toast.LENGTH_SHORT).show()
            return
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            allApps.map { it.appName }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinner.adapter = adapter
    }
    private fun setupBlockButton() {
        binding.blockApp.setOnClickListener {
            val selectedPosition = binding.spinner.selectedItemPosition
            val selectedApp = allApps[selectedPosition]
            blockApp(selectedApp)
        }
    }

    private fun blockApp(appInfo: AppInfo) {
        val blockedApp = hashMapOf(
            "packageName" to appInfo.packageName,
            "appName" to appInfo.appName,
            "blockedAt" to System.currentTimeMillis()
        )

        db.collection("blocked_apps")
            .document(appInfo.packageName)
            .set(blockedApp)
            .addOnSuccessListener {
                Toast.makeText(this, "${appInfo.appName} blocked", Toast.LENGTH_SHORT).show()
                loadBlockdAppps()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadBlockdAppps() {
        binding.forBlockedApps.removeAllViews()
        db.collection("blocked_apps")
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    val textView = TextView(this).apply {
                        text = "No apps blocked"
                        textSize = 16f
                        setPadding(20, 20, 20, 20)
                        alpha = 0.6f
                    }
                    binding.forBlockedApps.addView(textView)
                } else {
                    for (document in result) {
                        val appName = document.getString("appName") ?: ""
                        val packageName = document.getString("packageName") ?: ""
                        if (appName.isNotEmpty() && packageName.isNotEmpty()) {
                            addBlockedAppView(appName, packageName)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading blocked apps: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    private fun addBlockedAppView(appName: String, packageName: String) {
        val view = layoutInflater.inflate(R.layout.blocked_app_item, null)
        view.findViewById<TextView>(R.id.blockedAppName).text = appName
        view.findViewById<Button>(R.id.unblockButton).setOnClickListener {
            unblockApp(packageName, appName)
        }
        binding.forBlockedApps.addView(view)
    }

    private fun unblockApp(packageName: String, appName: String) {
        db.collection("blocked_apps")
            .document(packageName)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "$appName unblocked", Toast.LENGTH_SHORT).show()
                loadBlockdAppps()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error unblocking: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadTimeData() {
        db.collection("app_usage")
            .get()
            .addOnSuccessListener { result ->
                binding.forTimeUsage.removeAllViews()
                for (document in result) {
                    val packageName = document.id
                    val usageTime = document.getLong("totalTimeUsed") ?: 0

                    val minutes = usageTime / 60000
                    val seconds = (usageTime % 60000) / 1000

                    val appNameText = getAppName(packageName)
                    val usageText = "$appNameText - Usage: $minutes min, $seconds sec"

                    val textView = TextView(this).apply {
                        text = usageText
                        textSize = 16f
                        setPadding(20, 20, 20, 20)
                    }

                    binding.forTimeUsage.addView(textView)
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error loading data: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun getInstalledApps(): List<AppInfo> {
        val packageManager = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        return packageManager.queryIntentActivities(intent, 0)
            .map { resolveInfo ->
                AppInfo(
                    resolveInfo.activityInfo.packageName,
                    resolveInfo.loadLabel(packageManager).toString()
                )
            }
            .filter {
                it.packageName != packageName &&
                        !isSystemApp(it.packageName)
            }
            .sortedBy { it.appName }
    }
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            (applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        } catch (e: Exception) {
            false
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun isPermission(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val serviceName = "${packageName}/${UsageAccessibilityService::class.java.canonicalName}"
        return enabledServices?.contains(serviceName) == true
    }

    private fun requestAccessibilityPermission() {
        Toast.makeText(
            this,
            "Please enable Accessibility Service to track app usage",
            Toast.LENGTH_LONG
        ).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun updateBtnUI() {
        if (isPermission()) {
            binding.permissionForTracking.text = "Already started"
            binding.permissionForTracking.isEnabled = false
        } else {
            binding.permissionForTracking.text = "Start Tracking"
            binding.permissionForTracking.isEnabled = true
        }
    }

    override fun onResume() {
        super.onResume()
        updateBtnUI()
        loadBlockdAppps()
        loadTimeData()
    }
}

data class AppInfo(
    val packageName: String,
    val appName: String
)