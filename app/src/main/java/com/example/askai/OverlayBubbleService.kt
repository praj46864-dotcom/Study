package com.example.askai

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * A small floating button the user can see and move at all times (Android
 * requires SYSTEM_ALERT_WINDOW overlays to stay visible/interactive, so
 * this is never hidden from the user). Tapping it opens a tiny panel to
 * type a question; only then is a single screenshot taken and sent.
 */
class OverlayBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showBubble() {
        val button = Button(this).apply {
            text = "Ask AI"
            alpha = 0.9f
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 300

        // Simple drag-to-move handling.
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        button.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(button, params)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!moved) showQuestionPanel()
                    true
                }
                else -> false
            }
        }

        bubbleView = button
        windowManager.addView(button, params)
    }

    private fun showQuestionPanel() {
        if (panelView != null) return
        val inflater = LayoutInflater.from(this)
        val panel = inflater.inflate(R.layout.overlay_panel, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val input = panel.findViewById<EditText>(R.id.questionInput)
        val askButton = panel.findViewById<Button>(R.id.askButton)
        val cancelButton = panel.findViewById<Button>(R.id.cancelButton)
        val resultText = panel.findViewById<TextView>(R.id.resultText)

        askButton.setOnClickListener {
            val question = input.text.toString().ifBlank { "Is screen par kya hai aur mujhe aage kya karna chahiye?" }
            resultText.text = "Capturing..."
            // Hide the panel + bubble for a moment so they don't appear in
            // their own screenshot, then grab exactly one frame.
            setOverlayVisible(false)
            Handler(Looper.getMainLooper()).postDelayed({
                val bitmap = CaptureService.captureOnce()
                setOverlayVisible(true)
                if (bitmap == null) {
                    resultText.text = "Capture nahi ho paya, phir try karein."
                    return@postDelayed
                }
                val prefs = getSharedPreferences("ask_ai_prefs", MODE_PRIVATE)
                val apiKey = prefs.getString("api_key", "") ?: ""
                val baseUrl = prefs.getString("api_base_url", "https://api.openai.com/v1/chat/completions")
                    ?: "https://api.openai.com/v1/chat/completions"
                val model = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"

                if (apiKey.isBlank()) {
                    resultText.text = "Pehle app me API key save karein."
                    return@postDelayed
                }

                resultText.text = "Sochte huye..."
                AiClient.ask(apiKey, baseUrl, model, question, bitmap) { answer, error ->
                    resultText.text = answer ?: "Error: $error"
                }
            }, 200)
        }

        cancelButton.setOnClickListener { hideQuestionPanel() }

        panelView = panel
        windowManager.addView(panel, params)
    }

    private fun hideQuestionPanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
    }

    private fun setOverlayVisible(visible: Boolean) {
        val vis = if (visible) View.VISIBLE else View.INVISIBLE
        bubbleView?.visibility = vis
        panelView?.visibility = vis
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
