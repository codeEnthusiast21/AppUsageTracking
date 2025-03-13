package com.example.otherapptimedetection

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.otherapptimedetection.service.UsageTrackerService
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.LinearLayout
import android.widget.TextView
import com.example.otherapptimedetection.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var appUsageLayout: LinearLayout
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appUsageLayout = findViewById(R.id.appUsageLayout)

        // checking permission

        binding.startTrackingButton.setOnClickListener {
            if (!requiredPermission()) {
                requestPermission()
            } else {
                startTracking()
                binding.startTrackingButton.text= "Already started"
                binding.startTrackingButton.isEnabled = false
            }
        }

        // loading data from firestore
        loadData()
    }

    private fun requiredPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestPermission() {
        Toast.makeText(
            this,
            "Please grant usage access permission to track app usage",
            Toast.LENGTH_LONG
        ).show()
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun startTracking() {
        val serviceIntent = Intent(this, UsageTrackerService::class.java)
        startService(serviceIntent)
        Toast.makeText(this, "Usage tracking started", Toast.LENGTH_SHORT).show()
    }

    private fun loadData() {
        db.collection("app_usage")
            .get()
            .addOnSuccessListener { result ->
                appUsageLayout.removeAllViews()
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

                    appUsageLayout.addView(textView)
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error loading data: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun getAppName(packageName: String): String {
        try {
            val packageManager = applicationContext.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            return packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            return packageName
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}