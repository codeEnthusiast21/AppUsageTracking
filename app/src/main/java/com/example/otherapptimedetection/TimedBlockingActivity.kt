package com.example.otherapptimedetection

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.otherapptimedetection.databinding.ActivityTimedBlockingBinding
import com.google.firebase.firestore.FirebaseFirestore
import retrofit2.Call
import retrofit2.Response
import java.util.concurrent.TimeUnit

class TimedBlockingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimedBlockingBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var countDownTimer: CountDownTimer

    private val selectedApps = mutableSetOf<AppInfo>()
    private var isTimerRunning = false
    private var timeLeftInMillis: Long = 0
    private val updateInterval:Long=20000
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimedBlockingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        initializeViews()
        startFetchingQuotes()
    }

    private fun initializeViews() {
        // Initialize minutes picker
        binding.minutesPicker.apply {
            minValue = 0
            maxValue = 59
            value = 0
            wrapSelectorWheel = true
        }

        // Initialize seconds picker
        binding.secondsPicker.apply {
            minValue = 0
            maxValue = 59
            value = 30
            wrapSelectorWheel = true
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

            val minutes = binding.minutesPicker.value
            val seconds = binding.secondsPicker.value

            if (minutes == 0 && seconds == 0) {
                Toast.makeText(this, "Please set a duration greater than 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val durationMillis = (minutes * 60 * 1000L) + (seconds * 1000L)
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
        binding.minutesPicker.isEnabled = false
        binding.secondsPicker.isEnabled = false
    }

    private fun resetUI() {
        binding.selectAppsButton.isEnabled = true
        binding.startButton.isEnabled = true
        binding.minutesPicker.isEnabled = true
        binding.secondsPicker.isEnabled = true
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
    private fun startFetchingQuotes() {
        // Runnable to fetch a new quote every 20 seconds
        val runnable = object : Runnable {
            override fun run() {
                fetchQuote() // Fetch a new quote
                handler.postDelayed(this, updateInterval) // Schedule next update
            }
        }
        handler.post(runnable) // Start the first execution
    }

    private fun fetchQuote() {
        RetrofitClient.qouteApi.getRandomQuote().enqueue(object: retrofit2.Callback<List<QuotesModel>> {
            @SuppressLint("SetTextI18n")
            override fun onResponse(
                call: Call<List<QuotesModel>>,
                response: Response<List<QuotesModel>>,
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val quote = response.body()!![0] // Get first quote from list
                    binding.tvQuotes.text="\"${quote.q}\" - ${quote.a}"
                } else {
                    binding.tvQuotes.text="No data available"
                }
            }

            override fun onFailure(call: Call<List<QuotesModel>>, t: Throwable) {
                binding.tvQuotes.text= "Failed to load data"
            }
        },
        )
    }
}
