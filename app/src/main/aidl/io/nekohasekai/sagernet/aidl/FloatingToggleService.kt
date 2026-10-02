package io.nekohasekai.sagernet

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import io.nekohasekai.sagernet.database.DataStore

class FloatingToggleService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var bubble: TextView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            updateColor()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        bubble = TextView(this).apply {
            text = "⏻"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        params = WindowManager.LayoutParams(
            140, 140,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = e.rawX
                        touchY = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - touchX).toInt()
                        val dy = (e.rawY - touchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                        params.x = startX + dx
                        params.y = startY + dy
                        wm.updateViewLayout(bubble, params)
                    }
                    MotionEvent.ACTION_UP -> if (!moved) toggle()
                }
                return true
            }
        })

        wm.addView(bubble, params)
        handler.post(refresh)
    }

    private fun toggle() {
        if (DataStore.serviceState.canStop) {
            SagerNet.stopService()
        } else {
            SagerNet.startService()
        }
    }

    private fun updateColor() {
        val on = DataStore.serviceState.canStop
        bubble.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (on) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        wm.removeView(bubble)
        super.onDestroy()
    }
}
