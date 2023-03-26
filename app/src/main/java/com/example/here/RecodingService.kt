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

    companion object {
        const val DISPLAY_THRESHOLD = 0.3f
        const val DEFAULT_NUM_OF_RESULTS = 2
        const val DEFAULT_OVERLAP_VALUE = 0.5f
        const val YAMNET_MODEL = "soundclassifier_with_metadata.tflite"
        const val SPEECH_COMMAND_MODEL = "speech.tflite"
    }

    private var lastResults = Array(2) { "" }

    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
//    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    private val BUFFER_SIZE = SAMPLE_RATE * 1 * 16 * 2 / 16
    private val SAMPLE_RATE_CANDIDATES = arrayOf(16000, 11025, 22050, 44100)

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    private fun createAudioRecord(): AudioRecord? {
        var buffer: ByteArray? = null

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return null
        }

        for (sampleRate in SAMPLE_RATE_CANDIDATES) {
            val sizeInBytes = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                continue
            }

            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT, sizeInBytes)

            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                buffer = ByteArray(sizeInBytes)
                return audioRecord
            } else {
                audioRecord.release()
            }
        }

        return null
    }

    fun startAudioCapture() {
        val options = AudioClassifier.AudioClassifierOptions.builder()
            .setScoreThreshold(DISPLAY_THRESHOLD)
            .setMaxResults(DEFAULT_NUM_OF_RESULTS)
            .build()
        // create and configure the AudioClassifier object
        classifier = AudioClassifier.createFromFileAndOptions(this, YAMNET_MODEL, options)
        tensorAudio = classifier!!.createInputTensorAudio()
        tensorRecorder = classifier!!.createAudioRecord()

        // start the AudioRecord object
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }

        val mime = "audio/mp4a-latm"
        val bitRate = 64000
        val audioFormat = MediaFormat.createAudioFormat(mime, SAMPLE_RATE, 1)
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        val audioEncoder = MediaCodec.createEncoderByType(mime)
        audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        audioEncoder.start()

        val outputFilePath = "${externalCacheDir?.absolutePath}/audiorecordtest.mp4"
        val mediaMuxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

// Add a track to the MediaMuxer
        val trackIndex = mediaMuxer.addTrack(audioEncoder.outputFormat)
        mediaMuxer.start()

        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE)
        audioRecord?.startRecording()



        isRecording = true
        recordingThread = Thread(Runnable {
            val buffer = ShortArray(BUFFER_SIZE)
            while (isRecording) {
                audioRecord?.read(buffer, 0, BUFFER_SIZE)

                // classify the audio using the AudioClassifier object
                tensorAudio?.load(buffer)
                val results = classifier!!.classify(tensorAudio!!)[0]

                Log.d("RecodingService", "Results: $results")

                val label = results.categories[0].label

                if (label == lastResults[0] && lastResults[0] == lastResults[1]) {
                    if (label != "0 Background Noise") {
                        mNotificationManager.notify(nameNotificationId, mNotification
                            .setContentText(label)
                            .build())
                    }
                }

//                sendRecording(buffer = buffer)

                Thread.sleep(2000)
                // use the output of the classifier for further processing
            }
        })
        recordingThread?.start()
    }

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

        startAudioCapture()
//        initClassifier()
//        startAudioClassification()
//
//        CoroutineScope(Dispatchers.Default).launch {
//            delay(1000)
//            while (true) {
//                startRecording()
//
//                launch(Dispatchers.Default) {
//                    classifyAudio()
//                }
//                delay(2000)
//                stopRecoding()
//            }
//
//        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startRecording() {
        fileName = "${externalCacheDir?.absolutePath}/audiorecordtest.mp4"
        recorder = MediaRecorder(this).apply {
            setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(fileName)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

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
            .url("http://3.34.229.20:3000/audio")
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

    private fun stopRecoding() {
            recorder?.apply {
                stop()
                release()
            }

            val file = File("${externalCacheDir?.absolutePath}/audiorecordtest.mp4")

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
                .url("http://3.34.229.20:3000/audio")
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

        counter++
        recorder = null
    }
}


