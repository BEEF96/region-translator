package com.example.regiontranslator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var btnOverlay: Button
    private lateinit var btnStart: Button

    private val REQ_CAPTURE = 1001
    private val REQ_NOTIF = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ✅ 레이아웃에 있는 id 기준으로 안전하게 가져오기
        statusText = findViewById(R.id.statusText)
        btnOverlay = findViewById(R.id.btnOverlayPermission)
        btnStart = findViewById(R.id.btnStart)

        btnOverlay.setOnClickListener {
            openOverlayPermission()
        }

        btnStart.setOnClickListener {
            // 1) Android 13+ 알림 권한
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (!granted) {
                    statusText.text = "상태: 알림 권한 요청 중..."
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        REQ_NOTIF
                    )
                    return@setOnClickListener
                }
            }

            // 2) 오버레이 권한 확인
            if (!Settings.canDrawOverlays(this)) {
                statusText.text = "상태: 오버레이 권한 필요"
                openOverlayPermission()
                return@setOnClickListener
            }

            // 3) 화면 캡처 권한 요청
            requestScreenCapture()
        }
    }

    private fun openOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestScreenCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        statusText.text = "상태: 화면 캡처 권한 요청..."
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_CAPTURE) {
            if (resultCode != Activity.RESULT_OK || data == null) {
                statusText.text = "상태: 화면 캡처 권한 거부됨"
                return
            }

            val svc = Intent(this, OverlayCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }

            ContextCompat.startForegroundService(this, svc)
            statusText.text = "상태: 실행 중"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_NOTIF) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            statusText.text = if (ok) "상태: 알림 권한 허용됨" else "상태: 알림 권한 거부됨(작동 제한)"
        }
    }
}
