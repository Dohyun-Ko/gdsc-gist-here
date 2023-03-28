package com.example.here

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.IBinder
import android.provider.MediaStore.Audio
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Runnable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RecodingService: Service() {
    private var fileName: String = ""

    private var recorder: MediaRecorder? = null
    private var counter: Int = 0;

    private var classifier: AudioClassifier? = null
    private var tensorAudio: TensorAudio? = null
    private var tensorRecorder: AudioRecord? = null

    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mNotification: Notification.Builder

    private val nameNotificationId = 317

    private var userName: String? = null

    companion object {
        const val DISPLAY_THRESHOLD = 0.3f
        const val DEFAULT_NUM_OF_RESULTS = 2
        const val YAMNET_MODEL = "soundclassifier_with_metadata.tflite"
    }

    private var lastResults = Array(2) { "" }

    private var count = 0

    fun initClassifier() {

        val options = AudioClassifier.AudioClassifierOptions.builder()
            .setScoreThreshold(DISPLAY_THRESHOLD)
            .setMaxResults(DEFAULT_NUM_OF_RESULTS)
            .build()
        try {
            classifier = AudioClassifier.createFromFileAndOptions(this, YAMNET_MODEL, options)
            tensorAudio = classifier!!.createInputTensorAudio()
            tensorRecorder = classifier!!.createAudioRecord()
        } catch (e: java.lang.Exception)
        {
            Log.d("RecodingService", "Error: ${e.message}")
        }
    }

    fun startAudioClassification() {
        if (tensorRecorder!!.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return;
        }
        tensorRecorder!!.startRecording()
    }

    private fun classifyAudio() {
        tensorAudio!!.load(tensorRecorder!!)
        val results = classifier!!.classify(tensorAudio!!)[0]
        Log.d("RecodingService", "Results: $results")
        val label = results.categories[0].label

        if (label == lastResults[0] && lastResults[0] == lastResults[1]) {
            if (label == "0 Background Noise") {
                return
            } else {
                mNotificationManager.notify(nameNotificationId, mNotification
                    .setContentText(label)
                    .build())
            }
        }

        lastResults[1] = lastResults[0]
        lastResults[0] = label


    }

    private val classifyRunnable = Runnable {
        classifyAudio()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val sharedPreferences = getSharedPreferences("com.example.here", Context.MODE_PRIVATE)
        val name = sharedPreferences.getString("name", null)
        userName = name

        val channelId = "com.example.ServiceTest"
        val channelName = "MyServiceTestChannel"
        val notificationId = 316
        val channel = NotificationChannel(
            channelId, channelName,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Here")
            .setContentText("Recognizing...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setTicker("ticker")
            .build()

        startForeground(notificationId, notification)

        mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotification = Notification.Builder(this, channelId)
            .setContentTitle("Here")
            .setContentText("내 이름이 불렸어요!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setTicker("ticker2")



        //fileName = "${externalCacheDir?.absolutePath}/audiorecordtest$counter.mp4"

//        initClassifier()
//        startAudioClassification()

        CoroutineScope(Dispatchers.Default).launch {
            delay(1000)
            while (true) {

                startRecording(count)

//                launch(Dispatchers.Default) {
//                    classifyAudio()
//                }
                delay(3000)
                stopRecoding(count)

                if (count > 5) {
                    count = 0
                } else {
                    count++
                }
            }

        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startRecording(count: Int) {

        fileName = "${externalCacheDir?.absolutePath}/audiorecordtest-$count.mp4"
        recorder = MediaRecorder(this).apply {
            setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setAudioChannels(1)

            try {
                prepare()
            } catch (e: IOException)
            {
                Log.e("Recorder", "prepare $e failed")
            }

            start()
        }
    }

    private fun sendRecording(buffer: ShortArray) {
        val byteBuffer = ByteBuffer.allocate(buffer.size * 2)
        val byteOrder = ByteOrder.nativeOrder()
        byteBuffer.order(byteOrder)
        for (sample in buffer) {
            byteBuffer.putShort(sample)
        }
        val audioData = byteBuffer.array()

        val requestFile = RequestBody.create("audio/mp4".toMediaTypeOrNull(), audioData)

        val client = OkHttpClient.Builder()
            .connectTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val part = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "audiorecordtest.mp4", requestFile)
            .build()

        val request = Request.Builder()
            .url("http://34.64.162.201/predict?keyword=$userName")
            .post(part)
            .build()

        val response = client.newCall(request).execute()

        if (response.isSuccessful) {
            val responseBodyString = response.body?.string()
            Log.d("Recorder", "Success ${responseBodyString}")
            if (responseBodyString != null) {
                val json = JSONObject(responseBodyString)
                val text = json.getString("text")
                Log.d("Recorder", "Success $text")
                if (text.contains("hello")) {
                    mNotificationManager.notify(nameNotificationId, mNotification.build())
                }
            }
        } else {
            Log.d("Recorder", "Failed ${response.code}")
        }
    }

    private fun stopRecoding(count: Int) {
            recorder?.apply {
                stop()
                release()
            }

            val file = File("${externalCacheDir?.absolutePath}/audiorecordtest-$count.mp4")

            val requestFile = RequestBody.create("video/mp4".toMediaTypeOrNull(), file)

            val client = OkHttpClient.Builder()
                .connectTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(100, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val part = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, requestFile)
                .build()

            val request = Request.Builder()
                .url("http://34.64.162.201/predict?keyword=$userName")
                .post(part)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBodyString = response.body?.string()
                Log.d("Recorder", "Success ${responseBodyString}")
                if (responseBodyString != null) {
                    val json = JSONObject(responseBodyString)
                    val transcript = json.getString("transcript")
                    Log.d("Recorder", "Success $transcript")
                    if (transcript.contains(userName ?: "null")) {
                        mNotificationManager.notify(nameNotificationId, mNotification
                            .build())
                    }
                }
            } else {
                Log.d("Recorder", "Failed ${response.code}")
            }

        counter++
        recorder = null
    }
}


