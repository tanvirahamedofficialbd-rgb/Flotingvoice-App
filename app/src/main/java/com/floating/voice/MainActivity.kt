package com.floating.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            val prefs = getSharedPreferences("FloatingVoicePrefs", Context.MODE_PRIVATE)
            val tvLangStatus = findViewById<TextView>(R.id.tvLangStatus)
            val btnMicPerm = findViewById<Button>(R.id.btnMicPerm)
            val btnOverlayPerm = findViewById<Button>(R.id.btnOverlayPerm)
            val btnAccessibility = findViewById<Button>(R.id.btnAccessibility)
            val btnToggleLang = findViewById<Button>(R.id.btnToggleLang)

            fun updateLangText() {
                val lang = prefs.getString("lang", "bn-BD") ?: "bn-BD"
                tvLangStatus.text = if (lang == "bn-BD") "Language: বাংলা (bn-BD)" else "Language: English (en-US)"
            }
            updateLangText()

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
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Floating Voice খুঁজে অন করে দিন", Toast.LENGTH_LONG).show()
            }

            btnToggleLang.setOnClickListener {
                val current = prefs.getString("lang", "bn-BD") ?: "bn-BD"
                val nextLang = if (current == "bn-BD") "en-US" else "bn-BD"
                prefs.edit().putString("lang", nextLang).apply()
                updateLangText()
                Toast.makeText(this, "ভাষা পরিবর্তন হয়েছে", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
