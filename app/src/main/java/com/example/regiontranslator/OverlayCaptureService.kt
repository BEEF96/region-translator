package com.example.regiontranslator

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import kotlin.math.abs

class OverlayCaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        private const val NOTI_CHANNEL_ID = "region_translator"
        private const val NOTI_ID = 1001
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var selector: RegionSelectorView
    private lateinit var tvResult: TextView
    private lateinit var btnAuto: Button

    private var autoOn = false
    private var lastOcr = ""

    private lateinit var mediaProjection: MediaProjection
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val ocr = OcrEngine()
    private val tr = TranslateEngine()

    private var displayWidth = 0
    private var displayHeight = 0
    private var densityDpi = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTI_ID, buildNotification("영역 번역 실행 중"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        setupProjection(resultCode, data)
        showOverlay()

        scope.launch {
            tr.ensureModel()
        }

        return START_STICKY
    }

    private fun setupProjection(resultCode: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        val metrics = resources.displayMetrics
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
        densityDpi = metrics.densityDpi

        imageReader = ImageReader.newInstance(displayWidth, displayHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "RegionTranslatorDisplay",
            displayWidth, displayHeight, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun showOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_widget, null)
        selector = overlayView.findViewById(R.id.selector)
        tvResult = overlayView.findViewById(R.id.tvResult)

        val btnOnce = overlayView.findViewById<Button>(R.id.btnTranslateOnce)
        btnAuto = overlayView.findViewById(R.id.btnToggleAuto)
        val btnClose = overlayView.findViewById<Button>(R.id.btnClose)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        // Drag the whole widget by touching the region box
        selector.setOnTouchListener(DragTouchListener(params))

        btnOnce.setOnClickListener { scope.launch { translateOnce() } }

        btnAuto.setOnClickListener {
            autoOn = !autoOn
            btnAuto.text = if (autoOn) "자동: ON" else "자동: OFF"
            if (autoOn) scope.launch { autoLoop() }
        }

        btnClose.setOnClickListener {
            stopSelf()
        }

        windowManager.addView(overlayView, params)
    }

    private inner class DragTouchListener(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    return true
                }
            }
            return false
        }
    }

    private suspend fun autoLoop() {
        while (autoOn) {
            translateOnce()
            delay(900)
        }
    }

    private suspend fun translateOnce() {
        val bmp = captureBitmap() ?: run {
            tvResult.text = "캡처 실패(권한/상태 확인)"
            return
        }

        val cropRect = computeCropRectOnScreenBitmap()
        val croppedRaw = BitmapUtils.safeCrop(bmp, cropRect)
        val cropped = BitmapUtils.preprocessForOcr(croppedRaw)

        // OCR -> translate
        val ocrText = withContext(Dispatchers.Default) { ocr.recognize(cropped).trim() }
        if (ocrText.isBlank()) {
            tvResult.text = "영문 텍스트가 감지되지 않았어요."
            return
        }
        // 변화 없으면 번역 스킵 (배터리 절약)
        if (ocrText == lastOcr) return
        lastOcr = ocrText

        val ko = tr.translate(ocrText).trim()
        tvResult.text = if (ko.isNotBlank()) ko else "(번역 결과 없음)"
    }

    private fun computeCropRectOnScreenBitmap(): Rect {
    // selector 뷰의 화면상 좌표 + selector 내부 선택 사각형을 합쳐 최종 rect 계산
    val viewLoc = IntArray(2)
    selector.getLocationOnScreen(viewLoc)
    val selected = selector.getSelectedRectInView()

    val left = (viewLoc[0] + selected.left).toInt()
    val top = (viewLoc[1] + selected.top).toInt()
    val right = (viewLoc[0] + selected.right).toInt()
    val bottom = (viewLoc[1] + selected.bottom).toInt()

    return Rect(left, top, right, bottom)
}

    private fun captureBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        try {
            val plane = image.planes[0]
            val buffer: ByteBuffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * displayWidth

            val bmp = Bitmap.createBitmap(
                displayWidth + rowPadding / pixelStride,
                displayHeight,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)

            // padding 제거
            return Bitmap.createBitmap(bmp, 0, 0, displayWidth, displayHeight)
        } catch (e: Throwable) {
            return null
        } finally {
            image.close()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTI_CHANNEL_ID,
                "RegionTranslator",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NOTI_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("RegionTranslator")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        autoOn = false
        scope.cancel()

        try { windowManager.removeView(overlayView) } catch (_: Throwable) {}

        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { mediaProjection.stop() } catch (_: Throwable) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
