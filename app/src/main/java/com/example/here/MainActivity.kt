package com.example.here

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

        val sharedPreferences = getSharedPreferences("com.example.here", Context.MODE_PRIVATE)
        val name = sharedPreferences.getString("name", null)

        if (name == null) {
            startActivity(Intent(this, InitialActivity::class.java))
        } else {
            startActivity(Intent(this, AlertConfigureActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
}
