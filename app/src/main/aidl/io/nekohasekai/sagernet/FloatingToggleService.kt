package io.nekohasekai.sagernet

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ui.MainActivity

class FloatingToggleService : Service() {

    private var windowManager: WindowManager? = null
    private var button: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            updateColor()
            handler.postDelayed(this, 700)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (button == null) {
            if (!Settings.canDrawOverlays(this)) {
                stopSelf()
                return START_NOT_STICKY
            }
            createButton()
            handler.post(refresh)
        }
        return START_STICKY
    }

    private fun createButton() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val density = resources.displayMetrics.density
        val size = (48 * density).toInt()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = (200 * density).toInt()
        }

        val view = TextView(this).apply {
            text = "VPN"
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            alpha = 0.85f
        }
        button = view
        updateColor()

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    wm.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) toggle()
                    true
                }

                else -> false
            }
        }

        wm.addView(view, params)
    }

    private fun toggle() {
        if (DataStore.serviceState.canStop) {
            SagerNet.stopService()
        } else {
            val needVpnPermission =
                DataStore.serviceMode == Key.MODE_VPN && VpnService.prepare(this) != null
            if (needVpnPermission) {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                SagerNet.startService()
            }
        }
        handler.postDelayed({ updateColor() }, 500)
    }

    private fun updateColor() {
        val view = button ?: return
        val color = if (DataStore.serviceState.connected) {
            Color.parseColor("#2E7D32")
        } else {
            Color.parseColor("#C62828")
        }
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        button?.let { windowManager?.removeView(it) }
        button = null
        super.onDestroy()
    }
}
