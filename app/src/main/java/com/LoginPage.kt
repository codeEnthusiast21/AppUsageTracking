package com

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.otherapptimedetection.MainActivity
import com.example.otherapptimedetection.R
import com.example.otherapptimedetection.databinding.ActivityLoginPageBinding
import com.example.otherapptimedetection.databinding.ActivityTimedBlockingBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth

class LoginPage : AppCompatActivity() {
    private lateinit var binding: ActivityLoginPageBinding
    private lateinit var db:Firebase
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.loginButton.setOnClickListener() {
            val userEmail = binding.emailLogin.text.toString()
            val userPassword = binding.passwordLogin.text.toString()
            signInWithFirebase(userEmail,userPassword)
        }
        binding.registerText.setOnClickListener(){
            val intent = Intent(this,SignUp::class.java)
            startActivity(intent)
            finish()
        }

    }
    fun signInWithFirebase(userEmail:String, userPassword:String){
        auth.signInWithEmailAndPassword(userEmail,userPassword).addOnCompleteListener { task->
            if (task.isSuccessful){
                Toast.makeText(applicationContext,"Login successful", Toast.LENGTH_SHORT).show()
                val intent = Intent(this@LoginPage,MainActivity::class.java)
                startActivity(intent)
                finish()
            }
            else{
                Toast.makeText(applicationContext,task.exception?.localizedMessage, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    override fun onStart() {
        super.onStart()
        val user = auth.currentUser
        if (user!=null){
            Toast.makeText(applicationContext,"Login successful",Toast.LENGTH_SHORT).show()
            val intent = Intent(this@LoginPage,MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}