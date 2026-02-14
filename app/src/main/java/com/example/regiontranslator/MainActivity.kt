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

    private lateinit var status: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnStart: Button
    private var btnStop: Button? = null

    private val REQ_CAPTURE = 1001
    private val REQ_NOTIF = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ✅ activity_main.xml 실제 id에 맞춤
        status = findViewById(R.id.status)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnStart = findViewById(R.id.btnStart)
        btnStop = try { findViewById<Button>(R.id.btnStop) } catch (_: Exception) { null }

        btnGrantOverlay.setOnClickListener { openOverlayPermission() }

        btnStart.setOnClickListener {
            // 1) Android 13+ 알림 권한
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (!granted) {
                    status.text = "상태: 알림 권한 요청 중..."
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
                status.text = "상태: 오버레이 권한 필요"
                openOverlayPermission()
                return@setOnClickListener
            }

            // 3) 화면 캡처 권한 요청
            requestScreenCapture()
        }

        btnStop?.setOnClickListener {
            stopService(Intent(this, OverlayCaptureService::class.java))
            status.text = "상태: 중지됨"
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
        status.text = "상태: 화면 캡처 권한 요청..."
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_CAPTURE) {
            if (resultCode != Activity.RESULT_OK || data == null) {
                status.text = "상태: 화면 캡처 권한 거부됨"
                return
            }

            val svc = Intent(this, OverlayCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }

            ContextCompat.startForegroundService(this, svc)
            status.text = "상태: 실행 중"
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
            status.text = if (ok) "상태: 알림 권한 허용됨" else "상태: 알림 권한 거부됨(작동 제한)"
        }
    }
}
