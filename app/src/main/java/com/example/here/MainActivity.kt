package com.example.here

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.here.ui.theme.HereTheme
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KSuspendFunction1

private const val NAME_KEY = "com.example.key.name"

class MainActivity : ComponentActivity() {
    private val dataClient by lazy {
        Wearable.getDataClient(this)
    }

    private val clientDataViewModel by viewModels<ClientDataViewModel>()

    private var speechRecognizer: SpeechRecognizer? = null

    private var partialResultText = ""

    private suspend fun saveName(name: String) {
        try {
            val request = PutDataMapRequest.create("/name").apply {
                dataMap.putString("name", name)
            }
                .asPutDataRequest()
                .setUrgent()

            val result = dataClient.putDataItem(request).await()
        } catch (cancellationException: CancellationException) {
            Log.e("MainActivity", "Error saving name", cancellationException)
        } catch (exception: Exception) {
            Log.e("MainActivity", "Error saving name", exception)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
        }

        Log.d("MainActivity", "onCreate")

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        Log.d("SpeechRecognizer", "SpeechRecognizer created ${speechRecognizer.toString()}")

        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())

        speechRecognizer!!.setRecognitionListener(object: RecognitionListener{
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("SpeechRecognizer","Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
            }

            override fun onBufferReceived(buffer: ByteArray?) {

            }

            override fun onEndOfSpeech() {
                Log.d("SpeechRecognizer", "End of speech")
            }

            override fun onError(error: Int) {
                Log.d("SpeechRecognizer", "Error $error")
            }

            override fun onResults(results: Bundle?) {
                val resultText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).toString()
                Log.d("SpeechRecognizer", "result $resultText")
                setContent() {}
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResultText = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).toString()
                Log.d("SpeechRecognizer", "partialResult $partialResultText")
                setContent() {}
            }

            override fun onEvent(eventType: Int, params: Bundle?) {

            }

        })

        if (speechRecognizer != null)
            speechRecognizer?.startListening(speechRecognizerIntent)
        else
            Log.d("SpeechRecognizer", "SpeechRecognizer is null")

        setContent {
            HereTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    HomeScreen(saveName = ::saveName, partialResultText = partialResultText, speechRecognizer = speechRecognizer)
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

@Composable
fun HomeScreen(saveName: KSuspendFunction1<String, Unit>, partialResultText: String, speechRecognizer: SpeechRecognizer?) {
    var stage by remember { mutableStateOf(0 as Int) }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF242424))
            .clickable {
                when (stage) {
                    0, 1 -> stage++
                    2 -> {
                        if (name != "") {
                            stage++
                        }
                    }
                    3 -> {
                        stage++
                        val speechRecognizerIntent =
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        speechRecognizerIntent.putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        speechRecognizerIntent.putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE,
                            Locale.getDefault()
                        )
                        speechRecognizer?.startListening(speechRecognizerIntent)
                    }
                    else -> {}
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ImageSection(stage = stage)
        if (stage == 2) {
            TextField(value = name, onValueChange = { name = it } )
        } else {
            TalkingText(stage, name)
        }
        if (stage == 3) {
            Row() {
                Text(text = "Yes", modifier = Modifier
                    .padding(12.dp)
                    .clickable {
                        runBlocking {
                            launch {
                                saveName(name)
                            }.join()
                        }
                        stage++
                    })
                Text(text = "No", modifier = Modifier
                    .padding(12.dp)
                    .clickable { stage-- })
            }
        }
        if (stage == 4) {
            Text(partialResultText)
        }
    }
}

@Composable
fun ImageSection(stage: Int) {
    Row {
        when (stage) {
            0, 1, 2 -> Text(text = "👋")
            3 -> Text(text = "😃")
            4 -> Text(text = "??")
            5 -> Text(text = "😎")
            else -> {
                Text(text = "👋")
            }
        }
    }
}

@Composable
fun TalkingText(stage: Int, name: String) {
    val text = when (stage) {
        0 -> "Hi there!"
        1 -> "Can you tell me your name?"
        2 -> ""
        3 -> "Is your name $name?"
        4 -> "Call $name"
        5 -> "Now you are all set"
        else -> ""
    }

    Text(text = text)
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HereTheme {
    }
}