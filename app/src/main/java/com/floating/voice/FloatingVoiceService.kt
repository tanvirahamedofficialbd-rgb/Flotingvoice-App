package com.floating.voice

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.Toast
import kotlin.math.abs

class FloatingVoiceService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var micButton: ImageButton? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        mainHandler.post {
            try {
                createFloatingWidget()
                initSpeechRecognizer()
            } catch (e: Exception) {
                Toast.makeText(this, "Start Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    setMicColor("#E53935") // লাল (শুনছে)
                    Toast.makeText(this@FloatingVoiceService, "শুনছি... কথা বলুন", Toast.LENGTH_SHORT).show()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    setMicColor("#1E88E5") // নীল (স্বাভাবিক)
                }
                override fun onError(error: Int) {
                    isListening = false
                    setMicColor("#1E88E5")
                    Toast.makeText(this@FloatingVoiceService, "আবার চেষ্টা করুন (Code: $error)", Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    setMicColor("#1E88E5")
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        injectTextIntoFocusedField(matches[0])
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            Toast.makeText(this, "ফোনে Google Speech Service চালু নেই", Toast.LENGTH_LONG).show()
        }
    }

    private fun setMicColor(hexColor: String) {
        mainHandler.post {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hexColor))
            }
            micButton?.background = bg
        }
    }

    private fun createFloatingWidget() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_mic_layout, null)
        micButton = floatingView?.findViewById(R.id.btnFloatingMic)
        setMicColor("#1E88E5")

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 350
        }

        windowManager?.addView(floatingView, params)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        micButton?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < 15 && diffY < 15) {
                        toggleVoiceListening()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleVoiceListening() {
        mainHandler.post {
            try {
                if (isListening) {
                    speechRecognizer?.stopListening()
                    isListening = false
                    setMicColor("#1E88E5")
                    return@post
                }
                if (speechRecognizer == null) {
                    initSpeechRecognizer()
                }
                val prefs = getSharedPreferences("FloatingVoicePrefs", Context.MODE_PRIVATE)
                val lang = prefs.getString("lang", "bn-BD") ?: "bn-BD"

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Mic Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun injectTextIntoFocusedField(newText: String) {
        val nodeInfo = rootInActiveWindow ?: return
        val focusedNode = findFocusedEditableNode(nodeInfo)
        if (focusedNode == null) {
            Toast.makeText(this, "আগে কোনো লেখার বক্সে ট্যাপ করুন", Toast.LENGTH_SHORT).show()
            return
        }

        val existingText = focusedNode.text?.toString() ?: ""
        val updatedText = if (existingText.isEmpty()) newText else "$existingText $newText"
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updatedText)
        }
        val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        if (!success) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("VoiceText", "$newText "))
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
    }

    private fun findFocusedEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditableNode(child)
            if (result != null) return result
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        try {
            floatingView?.let { windowManager?.removeView(it) }
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
    }
}
