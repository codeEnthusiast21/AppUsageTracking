package com.example.otherapptimedetection

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.otherapptimedetection.databinding.ActivityTimedBlockingBinding
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class TimedBlockingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimedBlockingBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var countDownTimer: CountDownTimer

    private val selectedApps = mutableSetOf<AppInfo>()
    private var isTimerRunning = false
    private var timeLeftInMillis: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimedBlockingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        initializeViews()
    }

    private fun initializeViews() {
        // Initialize duration picker
        binding.durationPicker.apply {
            minValue = 1
            maxValue = 120
            value = 30
            wrapSelectorWheel = false
        }

        // Set up button click listeners
        binding.selectAppsButton.setOnClickListener {
            showAppSelectionDialog()
        }

        binding.startButton.setOnClickListener {
            if (selectedApps.isEmpty()) {
                Toast.makeText(this, "Please select apps to block", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val durationMinutes = binding.durationPicker.value
            val durationMillis = TimeUnit.MINUTES.toMillis(durationMinutes.toLong())
            startBlocking(durationMillis)
        }
    }

    private fun showAppSelectionDialog() {
        val installedApps = getInstalledApps()
        val appNames = installedApps.map { it.appName }.toTypedArray()
        val checkedItems = BooleanArray(appNames.size) {
            installedApps[it].packageName in selectedApps.map { app -> app.packageName }
        }

        AlertDialog.Builder(this)
            .setTitle("Select Apps to Block")
            .setMultiChoiceItems(appNames, checkedItems) { _, index, isChecked ->
                val app = installedApps[index]
                if (isChecked) {
                    addAppToBlock(app)
                } else {
                    removeAppFromBlock(app)
                }
            }
            .setPositiveButton("Done", null)
            .show()
    }

    private fun getInstalledApps(): List<AppInfo> {
        return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { !isSystemApp(it) }
            .map {
                AppInfo(
                    packageName = it.packageName,
                    appName = it.loadLabel(packageManager).toString()
                )
            }
            .sortedBy { it.appName }
    }

    private fun addAppToBlock(appInfo: AppInfo) {
        if (selectedApps.add(appInfo)) {
            addAppToUI(appInfo)
        }
    }

    private fun removeAppFromBlock(appInfo: AppInfo) {
        if (selectedApps.remove(appInfo)) {
            updateSelectedAppsUI()
        }
    }

    private fun addAppToUI(appInfo: AppInfo) {
        val appView = layoutInflater.inflate(R.layout.item_selected_app, null)
        appView.findViewById<android.widget.TextView>(R.id.appNameText).text = appInfo.appName
        appView.findViewById<android.widget.ImageButton>(R.id.removeButton).setOnClickListener {
            removeAppFromBlock(appInfo)
        }
        binding.selectedAppsContainer.addView(appView)
    }

    private fun updateSelectedAppsUI() {
        binding.selectedAppsContainer.removeAllViews()
        selectedApps.forEach { addAppToUI(it) }
    }

    private fun startBlocking(durationMillis: Long) {
        val endTime = System.currentTimeMillis() + durationMillis

        // Block all selected apps in Firestore
        val batch = db.batch()
        selectedApps.forEach { app ->
            val docRef = db.collection("blocked_apps").document(app.packageName)
            val data = hashMapOf(
                "packageName" to app.packageName,
                "appName" to app.appName,
                "blockedAt" to System.currentTimeMillis(),
                "unblockTime" to endTime,
                "isBlocked" to true
            )
            batch.set(docRef, data)
        }

        batch.commit()
            .addOnSuccessListener {
                startTimer(durationMillis)
                disableControls()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to block apps: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startTimer(duration: Long) {
        countDownTimer = object : CountDownTimer(duration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerDisplay(millisUntilFinished)
            }

            override fun onFinish() {
                unblockAllApps()
            }
        }.start()
        isTimerRunning = true
    }

    private fun updateTimerDisplay(millis: Long) {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        binding.timerText.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun unblockAllApps() {
        val batch = db.batch()
        selectedApps.forEach { app ->
            val docRef = db.collection("blocked_apps").document(app.packageName)
            batch.update(docRef, mapOf(
                "isBlocked" to false,
                "unblockTime" to System.currentTimeMillis()
            ))
        }

        batch.commit()
            .addOnSuccessListener {
                resetUI()
                Toast.makeText(this, "Apps unblocked", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to unblock apps: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun disableControls() {
        binding.selectAppsButton.isEnabled = false
        binding.startButton.isEnabled = false
        binding.durationPicker.isEnabled = false
    }

    private fun resetUI() {
        binding.selectAppsButton.isEnabled = true
        binding.startButton.isEnabled = true
        binding.durationPicker.isEnabled = true
        binding.timerText.text = "00:00"
        selectedApps.clear()
        binding.selectedAppsContainer.removeAllViews()
        isTimerRunning = false
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isTimerRunning) {
            countDownTimer.cancel()
        }
    }

    data class AppInfo(
        val packageName: String,
        val appName: String
    )
}