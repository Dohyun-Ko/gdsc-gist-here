package com.example.here

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class PauseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {

            Log.d("here", "pause receiver")
            it.stopService(Intent(it, RecodingService::class.java))

            val pauseServiceIntent = Intent(it, PausedService::class.java)
            ContextCompat.startForegroundService(it, pauseServiceIntent)
        }
    }
}
