package com.example.otherapptimedetection

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.otherapptimedetection.databinding.ActivityMainBinding
import com.example.otherapptimedetection.databinding.ActivityTimedBlockingBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response
import java.util.concurrent.TimeUnit
import javax.security.auth.callback.Callback

    class TimedBlockingActivity : AppCompatActivity() {
        private lateinit var binding: ActivityTimedBlockingBinding

        private val selectedApps = mutableSetOf<AppInfo>()
        private var isTimerRunning = false
        private var timeLeftInMillis: Long = 0
        private val updateInterval:Long=20000
        private val handler = Handler(Looper.getMainLooper())

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityTimedBlockingBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val db = FirebaseFirestore.getInstance()
            initializeViews()
            startFetchingQuotes()
        }

        private fun initializeViews() {

            val packageName: String
            val appName: String
        }
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