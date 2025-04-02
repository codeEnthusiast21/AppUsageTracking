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
import com.google.android.material.animation.AnimationUtils
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var allApps: List<AppInfo>
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val animation= android.view.animation.AnimationUtils.loadAnimation(this,R.anim.anim)
        binding.textView.startAnimation(animation)
        setupPermissionButton()
        binding.blockApp.setOnClickListener{
            startActivity(Intent(this,TimedBlockingActivity::class.java))
        }
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
        loadTimeData()
    }
}