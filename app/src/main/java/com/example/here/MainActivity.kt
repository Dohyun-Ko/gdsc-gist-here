package com.example.here

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.Manifest

val PERMISSIONS_REQUEST_CODE = 316
val PERMISSIONS = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.INTERNET,
    Manifest.permission.FOREGROUND_SERVICE,
    Manifest.permission.POST_NOTIFICATIONS
)



class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        ActivityCompat.requestPermissions(
            this,
            PERMISSIONS,
            PERMISSIONS_REQUEST_CODE
        )
//        onCheckPermission()

        val sharedPreferences = getSharedPreferences("com.example.here", Context.MODE_PRIVATE)
        val name = sharedPreferences.getString("name", null)

        //TODO: check if the user has already set a name
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

    private fun onCheckPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this, Manifest.permission.INTERNET
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this, Manifest.permission.FOREGROUND_SERVICE
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.RECORD_AUDIO
                )
                || ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.INTERNET
                )
                || ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.FOREGROUND_SERVICE
                )
                || ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            ) {
                Toast.makeText(this, "앱 실행을 위해서는 권한을 설정해야 합니다.", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS,
                    PERMISSIONS_REQUEST_CODE
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS, PERMISSIONS_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE-> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "앱 실행을 위한 권한이 설정 되었습니다", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "앱 실행을 위한 권한이 취소 되었습니다", Toast.LENGTH_SHORT).show()
                }
            }

        }
    }
}
