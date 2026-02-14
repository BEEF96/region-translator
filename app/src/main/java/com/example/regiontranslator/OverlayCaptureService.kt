package com.example.regiontranslator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class OverlayCaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wm: WindowManager
    private var overlayView: View? = null

    private var btnToggle: Button? = null
    private var txtStatus: TextView? = null
    private var txtResult: TextView? = null

    private var running = false
    private var job: Job? = null

    private val intervalMs = 500L

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundSafe()
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 여기서는 캡처/번역 연결은 다음 단계에서
        return START_STICKY
    }

    private fun startForegroundSafe() {
        val channelId = "rt_capture"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "RegionTranslator", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("RegionTranslator 실행 중")
            .setContentText("자막 감지(0.5초) 모드")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()

        startForeground(1001, notif)
    }

    private fun showOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_widget, null)

        btnToggle = overlayView!!.findViewById(R.id.btnToggle)
        txtStatus = overlayView!!.findViewById(R.id.txtStatus)
        txtResult = overlayView!!.findViewById(R.id.txtResult)

        btnToggle!!.setOnClickListener {
            if (!running) startAuto() else stopAuto()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        wm.addView(overlayView, params)
        txtStatus?.text = "상태: 대기"
        txtResult?.text = "번역 결과"
    }

    private fun startAuto() {
        running = true
        btnToggle?.text = "자동 번역 중지"
        txtStatus?.text = "상태: 감지 중(0.5초)"

        job?.cancel()
        job = scope.launch {
            var tick = 0
            while (running) {
                tick++
                txtStatus?.text = "상태: 감지 중(0.5초) / tick=$tick"
                delay(intervalMs)
            }
        }
    }

    private fun stopAuto() {
        running = false
        btnToggle?.text = "자동 번역 시작"
        txtStatus?.text = "상태: 대기"
        job?.cancel()
        job = null
    }

    override fun onDestroy() {
        stopAuto()
        try { wm.removeView(overlayView) } catch (_: Exception) {}
        overlayView = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
