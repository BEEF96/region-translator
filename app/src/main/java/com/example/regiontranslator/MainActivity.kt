package com.example.regiontranslator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var btnStop: Button

    private val REQUEST_OVERLAY = 101
    private val REQUEST_CAPTURE = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        btnStop = findViewById(R.id.btnStop)

        findViewById<Button>(R.id.btnGrantOverlay).setOnClickListener {
            requestOverlayPermission()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                status.text = "상태: 오버레이 권한이 필요해요."
                requestOverlayPermission()
                return@setOnClickListener
            }
            requestScreenCapture()
        }

        btnStop.setOnClickListener {
            stopService(Intent(this, OverlayCaptureService::class.java))
            status.text = "상태: 중지됨"
            btnStop.isEnabled = false
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            status.text = "상태: 오버레이 권한 OK"
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQUEST_OVERLAY)
    }

    private fun requestScreenCapture() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_OVERLAY -> {
                status.text = if (Settings.canDrawOverlays(this)) "상태: 오버레이 권한 OK" else "상태: 오버레이 권한 거부됨"
            }
            REQUEST_CAPTURE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    status.text = "상태: 캡처 권한 OK, 오버레이 시작"
                    val svc = Intent(this, OverlayCaptureService::class.java).apply {
                        putExtra(OverlayCaptureService.EXTRA_RESULT_CODE, resultCode)
                        putExtra(OverlayCaptureService.EXTRA_RESULT_DATA, data)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
                    btnStop.isEnabled = true
                } else {
                    status.text = "상태: 캡처 권한 거부됨"
                }
            }
        }
    }
}
