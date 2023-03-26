package com.example.here

import android.content.Context
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.here.ui.theme.HereTheme
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.reflect.KSuspendFunction1

class InitialActivity: ComponentActivity() {

    private val dataClient by lazy {
        Wearable.getDataClient(this)
    }

    private val clientDataViewModel by viewModels<ClientDataViewModel>()

    private val nameSaver by lazy {
        NameSaver(dataClient, context = this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("com.example.here", Context.MODE_PRIVATE)
        val name = sharedPreferences.getString("name", null)

        setContent {
            HereTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    HomeScreen(saveName = nameSaver::saveName)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(clientDataViewModel)
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(clientDataViewModel)
    }
}

class NameSaver(private val dataClient: DataClient, private val context: Context) {
    suspend fun saveName(name: String) {
        try {

            val sharedPreferences = context.getSharedPreferences("com.example.here", Context.MODE_PRIVATE)

            val editor = sharedPreferences.edit()
            editor.putString("name", name)
            editor.apply()

            val request = PutDataMapRequest.create("/name").apply {
                dataMap.putString("name", name)
            }
                .asPutDataRequest()
                .setUrgent()

            val result = dataClient.putDataItem(request).await()
        } catch (cancellationException: CancellationException) {
            Log.e("NameSaver", "Error saving name", cancellationException)
        } catch (exception: Exception) {
            Log.e("NameSaver", "Error saving name", exception)
        }
    }
}
@Composable
fun HomeScreen(saveName: KSuspendFunction1<String, Unit>) {
    var stage by remember { mutableStateOf(0 as Int) }
    var name by remember { mutableStateOf("") }

    fun advanceStage() {
        stage++
    }

    Box {
        StageContent(stage = stage, name = name, onNameChange = { name = it }, onStageAdvance = { stage++ }, onStageRegress = { stage-- }, saveName=saveName)

        if (stage == 4) {
            RecordingScreen(advanceStage = { stage++ })
        }
    }
}

@Composable
fun StageContent(
    stage: Int,
    name: String,
    onNameChange: (String) -> Unit,
    onStageAdvance: () -> Unit,
    onStageRegress: () -> Unit,
    saveName: KSuspendFunction1<String, Unit>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF242424))
            .clickable {
                when (stage) {
                    0, 1 -> onStageAdvance()
                    2 -> {
                        if (name != "") {
                            onStageAdvance()
                        }
                    }
                    3 -> onStageAdvance()
                    else -> {
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ImageSection(stage = stage)
        if (stage == 2) {
            NameInput(name = name, onNameChange = onNameChange)
        } else {
            TalkingText(stage, name)
        }
        if (stage == 3) {
            ConfirmationButtons(onConfirm = { runBlocking { launch { saveName(name) }.join() }; onStageAdvance() }, onCancel = onStageRegress)
        }
    }
}

@Composable
fun NameInput(name: String, onNameChange: (String) -> Unit) {
    BasicTextField(
        value = name, onValueChange = onNameChange,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        ),
        decorationBox = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                it()
                Box(modifier = Modifier.height(3.dp).width(270.dp).background(Color.White)) {}
            }
        },
    )
}

@Composable
fun ConfirmationButtons(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Row {
        Button(onClick = onConfirm) {
            Text(text = "Yes")
        }

        Button(onClick = onCancel) {
            Text(text = "No")
        }
    }
}

@Composable
fun RecordingScreen(advanceStage : () -> Unit) {
    var checked by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val response = sendRecording(context)
            Log.d("RecordingScreen", "Response: $response")
            checked = response != null
            if (checked) {
                delay(1000)
                advanceStage()
            }
        }
    }

    if (checked) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = 0.dp, y = 0.dp)
                .background(Color(0x7F242424)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painter = painterResource(R.drawable.check_green), contentDescription = "Green Check")
        }
    }
}


suspend fun sendRecording(context: Context): String? {
    val fileName = "${context.externalCacheDir?.absolutePath}/audiorecordtest.mp4"
    val recorder = MediaRecorder(context).apply {
        setAudioSource(MediaRecorder.AudioSource.DEFAULT)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(fileName)
        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

        try {
            prepare()
        } catch (e: IOException) {
            Log.e("Recorder", "prepare $e failed")
            return null
        }

        start()
    }

    delay(3000)

    recorder.stop()
    recorder.release()

    val file = File(fileName)

    return withContext(Dispatchers.IO) {
        val requestFile = RequestBody.create("video/mp4".toMediaTypeOrNull(), file)

        val client = OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
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
            responseBodyString?.let {
                val json = JSONObject(it)
                val text = json.getString("text")
                Log.d("Recorder", "Success $text")
                if (text.contains("hello")) {
                    text
                } else {
                    text
                }
            }
        } else {
            Log.d("Recorder", "Failed ${response.code}")
            null
        }
    }
}

@Composable
fun ImageSection(stage: Int) {
    Row {
        when (stage) {
            0, 1, 2 -> Image(painter = painterResource(R.drawable.waving_hand)
                , contentDescription = "Waving hand")
            3 -> Image(painter = painterResource(R.drawable.grinning_face) , contentDescription = "Grinning face" )
            4 -> Image(painter = painterResource(R.drawable.sound_graph) , contentDescription = "Sound Graph" )
            5 -> Image(painter = painterResource(R.drawable.sunglasses_face), contentDescription = "Sunglasses face" )
            else -> { }
        }
    }
}

@Composable
fun TalkingText(stage: Int, name: String) {
    val text = getTextForStage(stage, name)

    Text(text = text, fontSize = 30.sp, color = Color.White, fontWeight = FontWeight.Black)
}

fun getTextForStage(stage: Int, name: String): String {
    return when (stage) {
        0 -> "Hi there!"
        1 -> "Can you tell me your name?"
        2 -> ""
        3 -> "Is your name $name?"
        4 -> "Call $name"
        5 -> "Now you are all set"
        else -> ""
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HereTheme {
    }
}
