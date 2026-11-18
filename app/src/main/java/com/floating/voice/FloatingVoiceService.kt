package com.floating.voice

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class FloatingVoiceService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var micButton: ImageButton? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    private var isRecording = false
    private var isHolding = false
    private var isDragging = false
    private var usingGeminiMode = false

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

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(ms)
            }
        } catch (e: Exception) {}
    }

    private fun saveToHistory(text: String) {
        try {
            val prefs = getSharedPreferences("FloatingVoicePrefs", Context.MODE_PRIVATE)
            val oldArr = JSONArray(prefs.getString("history_list", "[]") ?: "[]")
            val newArr = JSONArray()
            newArr.put(text)
            for (i in 0 until minOf(oldArr.length(), 9)) {
                newArr.put(oldArr.getString(i))
            }
            prefs.edit().putString("history_list", newArr.toString()).apply()
        } catch (e: Exception) {}
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    setMicState("#FB8C00", 1.0f)
                }
                override fun onError(error: Int) {
                    isRecording = false
                    setMicState("#1E88E5", 0.65f)
                }
                override fun onResults(results: Bundle?) {
                    isRecording = false
                    setMicState("#1E88E5", 0.65f)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        saveToHistory(text)
                        injectTextIntoFocusedField(text)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun setMicState(hexColor: String, alphaValue: Float) {
        mainHandler.post {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hexColor))
            }
            micButton?.background = bg
            micButton?.alpha = alphaValue
        }
    }

    private fun createFloatingWidget() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_mic_layout, null)
        micButton = floatingView?.findViewById(R.id.btnFloatingMic)
        setMicState("#1E88E5", 0.65f)

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

        val holdRunnable = Runnable {
            if (isHolding && !isDragging) {
                startHoldRecording()
            }
        }

        micButton?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isHolding = true
                    isDragging = false
                    micButton?.alpha = 1.0f
                    mainHandler.postDelayed(holdRunnable, 150)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (!isRecording && (diffX > 20 || diffY > 20)) {
                        isDragging = true
                        mainHandler.removeCallbacks(holdRunnable)
                    }
                    if (isDragging) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isHolding = false
                    mainHandler.removeCallbacks(holdRunnable)
                    if (isRecording) {
                        stopHoldRecording()
                    } else {
                        micButton?.alpha = 0.65f
                    }
                    true
                }
                else -> false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startHoldRecording() {
        val prefs = getSharedPreferences("FloatingVoicePrefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "")?.trim() ?: ""

        if (apiKey.isNotEmpty()) {
            usingGeminiMode = true
            try {
                audioFile = File(cacheDir, "voice_record.m4a")
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(64000)
                    setAudioSamplingRate(16000)
                    setOutputFile(audioFile?.absolutePath)
                    prepare()
                    start()
                }
                isRecording = true
                vibrate(40)
                setMicState("#E53935", 1.0f)
            } catch (e: Exception) {
                isRecording = false
                setMicState("#1E88E5", 0.65f)
                Toast.makeText(this, "Mic Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            usingGeminiMode = false
            try {
                if (speechRecognizer == null) initSpeechRecognizer()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                }
                speechRecognizer?.startListening(intent)
                isRecording = true
                vibrate(40)
                setMicState("#E53935", 1.0f)
            } catch (e: Exception) {
                isRecording = false
                setMicState("#1E88E5", 0.65f)
            }
        }
    }

    private fun stopHoldRecording() {
        isRecording = false
        vibrate(25)
        if (usingGeminiMode) {
            setMicState("#FB8C00", 1.0f)
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                val file = audioFile
                if (file != null && file.exists()) {
                    sendAudioToGemini(file)
                } else {
                    setMicState("#1E88E5", 0.65f)
                }
            } catch (e: Exception) {
                mediaRecorder?.release()
                mediaRecorder = null
                setMicState("#1E88E5", 0.65f)
            }
        } else {
            setMicState("#FB8C00", 1.0f)
            speechRecognizer?.stopListening()
        }
    }

    private fun sendAudioToGemini(file: File) {
        val prefs = getSharedPreferences("FloatingVoicePrefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "")?.trim() ?: return

        Thread {
            try {
                val audioBytes = file.readBytes()
                val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 20000

                val prompt = "Transcribe this audio accurately. The speaker may speak in Bengali, English, or a natural mix of Bengali and English (code-switching). Write Bengali words in Bengali script and English words in English script naturally, with proper punctuation. Output ONLY the final transcribed text, nothing else."

                val inlineData = JSONObject().apply {
                    put("mime_type", "audio/mp4")
                    put("data", base64Audio)
                }
                val parts = JSONArray().apply {
                    put(JSONObject().put("text", prompt))
                    put(JSONObject().put("inline_data", inlineData))
                }
                val contents = JSONArray().put(JSONObject().put("parts", parts))
                val payload = JSONObject().put("contents", contents)

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }

                if (responseCode in 200..299) {
                    val json = JSONObject(responseText)
                    val text = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                        .trim()

                    mainHandler.post {
                        setMicState("#1E88E5", 0.65f)
                        if (text.isNotEmpty()) {
                            saveToHistory(text)
                            injectTextIntoFocusedField(text)
                        }
                    }
                } else {
                    mainHandler.post {
                        setMicState("#1E88E5", 0.65f)
                        Toast.makeText(this, "API Error ($responseCode)", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    setMicState("#1E88E5", 0.65f)
                    Toast.makeText(this, "Network Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
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
            mediaRecorder?.release()
        } catch (e: Exception) {}
    }
}
