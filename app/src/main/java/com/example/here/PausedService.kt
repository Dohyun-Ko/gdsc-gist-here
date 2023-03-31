package com.example.here

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class PausedService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        val channelId = "com.example.ServiceTest"
        val channelName = "MyServiceTestChannel"
        val notificationId = 316
        val channel = NotificationChannel(
            channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        val resumeIntent = Intent(this, RecodingService::class.java).also { intent ->
            Log.d("here", "resume intent")
            applicationContext.stopService(Intent(applicationContext, PausedService::class.java))
            applicationContext.startForegroundService(intent)
        }

        val resumePendingIntent: PendingIntent =
            PendingIntent.getService(this, 0, resumeIntent, PendingIntent.FLAG_IMMUTABLE)

        val action = NotificationCompat.Action.Builder(
            R.drawable.waving_hand,
            "Resume",
            resumePendingIntent
        ).build()

        val notification =
            NotificationCompat.Builder(this, channelId).setContentTitle("Here")
                .setContentText("Recognizing Paused").setSmallIcon(R.drawable.waving_hand)
                .setContentIntent(pendingIntent).setTicker("ticker")
                .addAction(action)
                .build()

        startForeground(notificationId, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "com.example.ServiceTest"
        val channelName = "MyServiceTestChannel"
        val notificationId = 316
        val channel = NotificationChannel(
            channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        val resumeIntent = Intent(this, RecodingService::class.java).also { intent ->
            Log.d("here", "resume intent")
            applicationContext.stopService(Intent(applicationContext, PausedService::class.java))
            applicationContext.startForegroundService(intent)
        }

        val resumePendingIntent: PendingIntent =
            PendingIntent.getService(this, 0, resumeIntent, PendingIntent.FLAG_IMMUTABLE)

        val action = NotificationCompat.Action.Builder(
            R.drawable.waving_hand,
            "Resume",
            resumePendingIntent
        ).build()

        val notification =
            NotificationCompat.Builder(this, channelId).setContentTitle("Here")
                .setContentText("Recognizing Paused").setSmallIcon(R.drawable.waving_hand)
                .setContentIntent(pendingIntent).setTicker("ticker")
                .addAction(action)
                .build()

        startForeground(notificationId, notification)

        return super.onStartCommand(intent, flags, startId)
    }
}