package com.bakhawone.thesis_bakhawone

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val btnStartScan: Button = findViewById(R.id.btnStartScan)

        btnStartScan.setOnClickListener {
            val intent = Intent(this, CamActivity::class.java)
            startActivity(intent)
        }
    }
}
