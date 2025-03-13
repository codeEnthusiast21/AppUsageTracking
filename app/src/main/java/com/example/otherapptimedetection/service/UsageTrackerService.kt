package com.example.otherapptimedetection.service


import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.app.Service
import android.os.IBinder
import com.google.firebase.firestore.FirebaseFirestore


class UsageTrackerService : Service() {

    private val db = FirebaseFirestore.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 1000L
    private var lastForegroundApp = ""
    private var lastTimeStamp: Long = 0

    private val appUsageTimes = HashMap<String, Long>()


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTracking()
        return START_STICKY
    }

    private fun startTracking() {
        lastTimeStamp = System.currentTimeMillis()


        handler.post(object : Runnable {
            override fun run() {
                checkCurrentAppUsage()
                handler.postDelayed(this, checkInterval)
            }
        })
    }

    private fun checkCurrentAppUsage() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - checkInterval


        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()


        var currentApp = ""
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentApp = event.packageName
            }
        }

        if (currentApp.isNotEmpty() && currentApp != packageName) {
            processAppUsage(currentApp)
        }
    }

    private fun processAppUsage(currentApp: String) {
        val currentTime = System.currentTimeMillis()

        if (lastForegroundApp.isNotEmpty() && lastForegroundApp != currentApp) {
            val usageTime = currentTime - lastTimeStamp

            appUsageTimes[lastForegroundApp] = (appUsageTimes[lastForegroundApp] ?: 0) + usageTime

            updateFirestore(lastForegroundApp, appUsageTimes[lastForegroundApp] ?: 0)
        }

        lastForegroundApp = currentApp
        lastTimeStamp = currentTime
    }

    private fun updateFirestore(packageName: String, totalTime: Long) {
        val appData = hashMapOf(
            "packageName" to packageName,
            "totalTimeUsed" to totalTime,
            "lastUpdated" to System.currentTimeMillis()
        )

        db.collection("app_usage")
            .document(packageName)
            .set(appData)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}