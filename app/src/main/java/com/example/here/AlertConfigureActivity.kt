package com.example.here

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.here.ui.theme.HereTheme
import com.google.android.gms.wearable.Wearable

class AlertConfigureActivity: ComponentActivity() {

    private val dataClient by lazy {
        Wearable.getDataClient(this)
    }

    private val clientDataViewModel by viewModels<ClientDataViewModel>()

    private val nameSaver by lazy {
        NameSaver(dataClient, context = this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Intent(this, RecodingService::class.java).also { intent ->
            applicationContext.startForegroundService(intent)
        }

        setContent {
            HereTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    IconGrid()
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
fun IconGrid() {
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF242424))) {
        items(10) {
            IconButton()
        }
    }

}

@Composable
fun IconButton() {
    Box(modifier = Modifier
        .fillMaxSize()
        .background(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0x00FFFFFF),
                    Color(0x20FFFFFF),
                )
            )
        )
    )
        {
            Image(painter = painterResource(id = R.drawable.check_green), contentDescription = "Green Check")
        }
}