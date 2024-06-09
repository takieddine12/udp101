package com.thanos.udpcalling

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val senderBtn = findViewById<Button>(R.id.sender);
        val receiverBtn = findViewById<Button>(R.id.receiver);

        senderBtn.setOnClickListener {
            Intent(this,MainActivity::class.java).apply {
                startActivity(this)
            }
        }
        receiverBtn.setOnClickListener {
            Intent(this,ReceiverActivity::class.java).apply {
                startActivity(this)
            }
        }
    }
}