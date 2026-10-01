package com.example.askai

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView

    private val mediaProjectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Hand the one-time grant straight to the foreground service.
                // Nothing is captured yet — only once the user later taps the
                // floating button does a single frame get grabbed.
                val intent = Intent(this, CaptureService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                ContextCompat.startForegroundService(this, intent)
                statusText.text = "Running. Tap the floating button on any screen to ask a question about it."
            } else {
                Toast.makeText(this, "Screen-share permission denied — can't start.", Toast.LENGTH_LONG).show()
            }
        }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                requestMediaProjection()
            } else {
                Toast.makeText(this, "Overlay permission is required for the floating button.", Toast.LENGTH_LONG).show()
            }
        }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Notifications are required so you always see when capture is active.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("ask_ai_prefs", MODE_PRIVATE)
        statusText = findViewById(R.id.statusText)

        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val apiBaseUrlInput = findViewById<EditText>(R.id.apiBaseUrlInput)
        val modelInput = findViewById<EditText>(R.id.modelInput)

        apiKeyInput.setText(prefs.getString("api_key", ""))
        apiBaseUrlInput.setText(prefs.getString("api_base_url", "https://api.openai.com/v1/chat/completions"))
        modelInput.setText(prefs.getString("model", "gpt-4o-mini"))

        findViewById<Button>(R.id.saveKeyButton).setOnClickListener {
            prefs.edit()
                .putString("api_key", apiKeyInput.text.toString().trim())
                .putString("api_base_url", apiBaseUrlInput.text.toString().trim())
                .putString("model", modelInput.text.toString().trim())
                .apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (prefs.getString("api_key", "").isNullOrBlank()) {
                Toast.makeText(this, "Pehle API key save karein.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startFlow()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, CaptureService::class.java))
            stopService(Intent(this, OverlayBubbleService::class.java))
            statusText.text = "Stopped."
        }
    }

    private fun startFlow() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            requestMediaProjection()
        }
    }

    private fun requestMediaProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
}
