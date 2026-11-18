package com.floating.voice

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray

class MainActivity : Activity() {
    private lateinit var tvStatusSummary: TextView
    private lateinit var historyContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            val prefs = getSharedPreferences("FloatingVoicePrefs", Context.MODE_PRIVATE)
            tvStatusSummary = findViewById(R.id.tvStatusSummary)
            historyContainer = findViewById(R.id.historyContainer)

            val btnMicPerm = findViewById<Button>(R.id.btnMicPerm)
            val btnOverlayPerm = findViewById<Button>(R.id.btnOverlayPerm)
            val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
            val etApiKey = findViewById<EditText>(R.id.etApiKey)
            val btnSaveKey = findViewById<Button>(R.id.btnSaveKey)
            val btnClearHistory = findViewById<Button>(R.id.btnClearHistory)
            val btnPrivacy = findViewById<Button>(R.id.btnPrivacy)

            etApiKey.setText(prefs.getString("gemini_api_key", ""))

            btnMicPerm.setOnClickListener {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            }

            btnOverlayPerm.setOnClickListener {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Overlay পারমিশন আগেই চালু আছে", Toast.LENGTH_SHORT).show()
                }
            }

            btnAccessibility.setOnClickListener {
                showAccessibilityDisclosureDialog()
            }

            btnSaveKey.setOnClickListener {
                val key = etApiKey.text.toString().trim()
                prefs.edit().putString("gemini_api_key", key).apply()
                if (key.isNotEmpty()) {
                    Toast.makeText(this, "API Key সেভ হয়েছে! মিক্সড বাংলা-ইংরেজি চালু।", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "API Key মুছে ফেলা হয়েছে (সাধারণ মোড)", Toast.LENGTH_SHORT).show()
                }
            }

            btnClearHistory.setOnClickListener {
                prefs.edit().remove("history_list").apply()
                loadHistory()
                Toast.makeText(this, "হিস্ট্রি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
            }

            btnPrivacy.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Privacy Policy & Data Safety")
                    .setMessage(
                        "Floating Voice AI respects your privacy:\n\n" +
                        "1. Accessibility Service: Used strictly to detect active text input fields and insert your dictated text.\n" +
                        "2. Audio Data: Recorded only while you hold the mic button. Sent directly to Google Speech or your own Gemini API key for transcription.\n" +
                        "3. No Data Collection: We do not store, sell, or share your personal data or voice recordings on any external server."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        loadHistory()
    }

    private fun showAccessibilityDisclosureDialog() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Service Disclosure")
            .setMessage(
                "Floating Voice AI uses the Android AccessibilityService API to:\n\n" +
                "- Display the floating microphone button over other apps.\n" +
                "- Detect the currently focused text box and automatically insert your voice-to-text transcription.\n\n" +
                "This app does NOT read your passwords, messages, or personal screen content. Do you agree to enable this service?"
            )
            .setPositiveButton("Agree & Continue") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Decline", null)
            .show()
    }

    private fun updatePermissionStatus() {
        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val overlayOk = Settings.canDrawOverlays(this)
        val accOk = isAccessibilityEnabled()

        val micText = if (micOk) "✔ Mic: Active" else "✖ Mic: Required"
        val ovText = if (overlayOk) "✔ Overlay: Active" else "✖ Overlay: Required"
        val accText = if (accOk) "✔ Accessibility: Active" else "✖ Accessibility: Required"

        tvStatusSummary.text = "$micText   |   $ovText\n$accText"
        tvStatusSummary.setTextColor(if (micOk && overlayOk && accOk) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(packageName)
    }

    private fun loadHistory() {
        historyContainer.removeAllViews()
        val prefs = getSharedPreferences("FloatingVoicePrefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("history_list", "[]") ?: "[]"
        try {
            val arr = JSONArray(jsonStr)
            if (arr.length() == 0) {
                val tv = TextView(this).apply {
                    text = "এখনও কোনো ভয়েস টেক্সট নেই।"
                    setTextColor(Color.GRAY)
                    setPadding(0, 8, 0, 8)
                }
                historyContainer.addView(tv)
                return
            }
            for (i in 0 until arr.length()) {
                val itemText = arr.getString(i)
                val tv = TextView(this).apply {
                    text = "• $itemText"
                    textSize = 14f
                    setTextColor(Color.parseColor("#263238"))
                    setPadding(8, 12, 8, 12)
                    setOnClickListener {
                        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("CopiedVoice", itemText))
                        Toast.makeText(this@MainActivity, "কপি হয়েছে!", Toast.LENGTH_SHORT).show()
                    }
                }
                historyContainer.addView(tv)
            }
        } catch (e: Exception) {}
    }
}
