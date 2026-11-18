package com.floating.voice

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.Toast

class FloatingVoiceService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            createFloatingWidget()
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            } else {
                Toast.makeText(this, "Speech Recognition available নয়", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createFloatingWidget() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_mic_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        windowManager?.addView(floatingView, params)

        val micButton = floatingView?.findViewById<ImageButton>(R.id.btnFloatingMic)
        micButton?.setOnClickListener {
            startVoiceListening()
        }
    }

    private fun startVoiceListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Toast.makeText(this@FloatingVoiceService, "শুনছি... কথা বলুন", Toast.LENGTH_SHORT).show()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    Toast.makeText(this@FloatingVoiceService, "আবার চেষ্টা করুন (Code: $error)", Toast.LENGTH_SHORT).show()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0]
                        injectTextIntoFocusedField(spokenText)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Mic Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun injectTextIntoFocusedField(newText: String) {
        val nodeInfo = rootInActiveWindow ?: return
        val focusedNode = findFocusedEditableNode(nodeInfo)
        focusedNode?.let {
            val existingText = it.text?.toString() ?: ""
            val updatedText = if (existingText.isEmpty()) newText else "$existingText $newText"
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updatedText)
            }
            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
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
