package com.millmitra.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.millmitra.app.MillMitraApp
import com.millmitra.app.R
import com.millmitra.app.ui.MainActivity
import com.millmitra.app.ui.VoiceInputActivity
import com.millmitra.app.util.TextSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground Service hosting the MillMitra floating draggable bubble
 * and response card overlay.
 */
class MillMitraOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager

    // Overlay Views
    private var bubbleView: View? = null
    private var cardView: View? = null

    // WindowManager LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var cardParams: WindowManager.LayoutParams

    // Card UI references
    private var tvModeBadge: TextView? = null
    private var tvInputPreview: TextView? = null
    private var tvOutputText: TextView? = null
    private var progressBar: ProgressBar? = null
    private var btnCopyClipboard: Button? = null
    private var btnShareWhatsApp: Button? = null
    private var btnLaunchSystemVoice: Button? = null
    private var layoutVocabulary: View? = null
    private var tvVocabContent: TextView? = null
    private var layoutDecodeInput: View? = null
    private var etInputText: EditText? = null
    private var btnPasteClipboard: View? = null
    private var btnClearInput: View? = null
    private var btnTranslateAction: Button? = null

    // Native SpeechRecognizer
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private var isCardShowing = false

    private val hideDismissRunnable = Runnable {
        bubbleView?.findViewById<ImageButton>(R.id.btnDismissOverlay)?.let { dismissView ->
            dismissView.animate().alpha(0f).setDuration(200).withEndAction {
                dismissView.visibility = View.GONE
            }.start()
        }
    }

    // App repository & settings
    private val app by lazy { MillMitraApp.instance }
    private val repository by lazy { app.repository }
    private val clipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            // Must start foreground notification immediately to satisfy Android foreground service policy
            createNotificationChannel()
            startForegroundServiceWithNotification()

            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // Guard against missing overlay permission to prevent BadTokenException crash
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please allow 'Display over other apps' in Settings first.", Toast.LENGTH_LONG).show()
                stopSelf()
                return
            }

            isRunning = true

            initBubbleView()
            initCardView()
        } catch (e: Exception) {
            android.util.Log.e("MillMitra", "Critical error in onCreate", e)
            Toast.makeText(this, "Service start error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PROCESS_VOICE_TEXT -> {
                val spokenText = intent.getStringExtra(EXTRA_VOICE_TEXT)
                if (!spokenText.isNullOrBlank()) {
                    processRecognizedVoiceText(spokenText)
                }
            }
        }
        return START_STICKY
    }

    /**
     * Initializes the floating draggable bubble widget.
     */
    private fun initBubbleView() {
        try {
            val themedContext = androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_MillMitra)
            val layoutInflater = LayoutInflater.from(themedContext)
            bubbleView = layoutInflater.inflate(R.layout.overlay_bubble, null)

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val displayMetrics = resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels

            bubbleParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 16
                // Center vertically on the left edge of the screen so thumb can easily reach it
                y = (screenHeight / 2) - (45 * displayMetrics.density).toInt()
            }

            val ivDragHandle = bubbleView?.findViewById<ImageView>(R.id.ivDragHandle)
            val btnTranslate = bubbleView?.findViewById<ImageButton>(R.id.btnActionTranslate)
            val btnMic = bubbleView?.findViewById<ImageButton>(R.id.btnActionMic)
            val btnDismiss = bubbleView?.findViewById<ImageButton>(R.id.btnDismissOverlay)

            // Unified Executive Platinum Palette for floating capsule dock
            ivDragHandle?.setColorFilter(android.graphics.Color.parseColor("#F1F5F9"))
            btnTranslate?.setColorFilter(android.graphics.Color.parseColor("#F8FAFC"))
            btnMic?.setColorFilter(android.graphics.Color.parseColor("#F8FAFC"))
            btnDismiss?.setColorFilter(android.graphics.Color.WHITE)

            // Reveal dismiss button on tap & hold (long press)
            val showDismissAction = {
                btnDismiss?.let { dismissView ->
                    if (dismissView.visibility != View.VISIBLE) {
                        dismissView.visibility = View.VISIBLE
                        dismissView.alpha = 0f
                        dismissView.animate().alpha(1f).setDuration(200).start()
                        Toast.makeText(this, "Tap ✕ to close floating assistant", Toast.LENGTH_SHORT).show()

                        dismissView.removeCallbacks(hideDismissRunnable)
                        dismissView.postDelayed(hideDismissRunnable, 5000)
                    }
                }
            }

            // Setup drag-and-snap gesture handling across the entire floating capsule dock
            val draggableBubble = bubbleView as? com.millmitra.app.ui.view.DraggableBubbleLayout
            draggableBubble?.setup(windowManager, bubbleParams)
            draggableBubble?.setOnClickListener {
                toggleCardVisibility()
            }
            draggableBubble?.setOnBubbleLongClickListener {
                showDismissAction()
            }

            ivDragHandle?.setOnClickListener {
                toggleCardVisibility()
            }
            ivDragHandle?.setOnLongClickListener {
                showDismissAction()
                true
            }

            // Dismiss Button Action
            btnDismiss?.setOnClickListener {
                Toast.makeText(this, "MillMitra assistant closed", Toast.LENGTH_SHORT).show()
                stopSelf()
            }

            // Translate / Decode Action
            btnTranslate?.setOnClickListener {
                openDecodeMode()
            }

            // Dictate / SpeechRecognizer Action
            btnMic?.setOnClickListener {
                handleDictateAction()
            }

            windowManager.addView(bubbleView, bubbleParams)
        } catch (e: Exception) {
            android.util.Log.e("MillMitra", "Failed to initBubbleView", e)
            Toast.makeText(this, "Failed to display overlay: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Initializes the floating dismissible pop-up card.
     */
    private fun initCardView() {
        try {
            val themedContext = androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_MillMitra)
            val layoutInflater = LayoutInflater.from(themedContext)
            cardView = layoutInflater.inflate(R.layout.overlay_popup_card, null)

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val displayMetrics = resources.displayMetrics
            val maxAllowedWidth = (380 * displayMetrics.density).toInt()
            val screenTargetWidth = (displayMetrics.widthPixels * 0.92f).toInt()
            val cardWidth = screenTargetWidth.coerceAtMost(maxAllowedWidth)

            cardParams = WindowManager.LayoutParams(
                cardWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            tvModeBadge = cardView?.findViewById(R.id.tvModeBadge)
            tvInputPreview = cardView?.findViewById(R.id.tvInputPreview)
            tvOutputText = cardView?.findViewById(R.id.tvOutputText)
            progressBar = cardView?.findViewById(R.id.progressBar)
            btnCopyClipboard = cardView?.findViewById(R.id.btnCopyClipboard)
            btnShareWhatsApp = cardView?.findViewById(R.id.btnShareWhatsApp)
            btnLaunchSystemVoice = cardView?.findViewById(R.id.btnLaunchSystemVoice)
            btnLaunchSystemVoice?.setOnClickListener {
                launchSystemVoiceInput()
            }
            layoutVocabulary = cardView?.findViewById(R.id.layoutVocabulary)
            tvVocabContent = cardView?.findViewById(R.id.tvVocabContent)
            layoutDecodeInput = cardView?.findViewById(R.id.layoutDecodeInput)
            etInputText = cardView?.findViewById(R.id.etInputText)
            btnPasteClipboard = cardView?.findViewById(R.id.btnPasteClipboard)
            btnClearInput = cardView?.findViewById(R.id.btnClearInput)
            btnTranslateAction = cardView?.findViewById(R.id.btnTranslateAction)
            val btnClose = cardView?.findViewById<ImageButton>(R.id.btnCloseCard)

            btnClose?.setOnClickListener {
                hideCard()
            }

            btnPasteClipboard?.setOnClickListener {
                try {
                    val clip = clipboardManager.primaryClip
                    val clipText = if (clip != null && clip.itemCount > 0) {
                        clip.getItemAt(0)?.text?.toString()?.trim()
                    } else null

                    if (!clipText.isNullOrBlank()) {
                        etInputText?.setText(clipText)
                        etInputText?.setSelection(clipText.length)
                        Toast.makeText(this, "Message pasted!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Clipboard is empty. Copy a message in WhatsApp first!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not access clipboard: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            btnClearInput?.setOnClickListener {
                etInputText?.setText("")
                tvOutputText?.text = ""
                layoutVocabulary?.visibility = View.GONE
                tvVocabContent?.text = ""
                btnCopyClipboard?.visibility = View.GONE
                btnShareWhatsApp?.visibility = View.GONE
            }

            btnTranslateAction?.setOnClickListener {
                executeDecodeTranslation()
            }

            btnCopyClipboard?.setOnClickListener {
                val textToCopy = tvOutputText?.text?.toString()
                if (!textToCopy.isNullOrBlank()) {
                    val sanitized = TextSanitizer.sanitizeForDisplay(textToCopy)
                    val clip = ClipData.newPlainText("MillMitra", sanitized)
                    clipboardManager.setPrimaryClip(clip)
                    Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }
            }

            btnShareWhatsApp?.setOnClickListener {
                val textToShare = tvOutputText?.text?.toString()
                if (!textToShare.isNullOrBlank()) {
                    val sanitized = TextSanitizer.sanitizeForDisplay(textToShare)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, sanitized)
                        setPackage("com.whatsapp")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try {
                        startActivity(sendIntent)
                        hideCard()
                    } catch (e: Exception) {
                        val chooser = Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, sanitized)
                            },
                            "Share via WhatsApp"
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(chooser)
                        hideCard()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MillMitra", "Failed to initCardView", e)
        }
    }

    /**
     * Action 1: Opens Decode Mode in the overlay card allowing user to paste or type an English mill message.
     */
    private fun openDecodeMode() {
        val apiKey = app.getApiKey()
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            showCard(
                modeTitle = "Setup Required",
                inputPreview = "Gemini API Key missing",
                initialOutput = "Please open the MillMitra app to configure your Gemini API Key.",
                showProgress = false,
                isDecodeMode = false
            )
            return
        }

        // Auto pre-fill if clipboard currently has English text and input is empty
        try {
            val clip = clipboardManager.primaryClip
            val clipboardText = if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0)?.text?.toString()?.trim()
            } else null

            if (!clipboardText.isNullOrBlank() && etInputText?.text.isNullOrBlank()) {
                etInputText?.setText(clipboardText)
                etInputText?.setSelection(clipboardText.length)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        showCard(
            modeTitle = "Decode: English ➔ Hindi",
            inputPreview = "",
            initialOutput = "Paste or type a mill message above, then tap 'Decode (अर्थ)'.",
            showProgress = false,
            isDecodeMode = true
        )
    }

    /**
     * Executes translation of the message currently inside etInputText.
     */
    private fun executeDecodeTranslation() {
        val textToTranslate = etInputText?.text?.toString()?.trim()
        if (textToTranslate.isNullOrBlank()) {
            Toast.makeText(this, "Please paste or type an English message first", Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = app.getApiKey()
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            Toast.makeText(this, "Please configure your Gemini API Key in MillMitra", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
        progressBar?.visibility = View.VISIBLE
        tvOutputText?.text = "Decoding message with mill terms preserved..."
        layoutVocabulary?.visibility = View.GONE
        tvVocabContent?.text = ""

        serviceScope.launch {
            val result = repository.translateEnglishToHinglish(textToTranslate, apiKey)
            progressBar?.visibility = View.GONE
            result.onSuccess { translatedText ->
                val parsed = TextSanitizer.splitReportAndVocab(translatedText)
                tvOutputText?.text = parsed.reportText
                btnCopyClipboard?.text = getString(R.string.copy_to_clipboard)
                btnCopyClipboard?.visibility = View.VISIBLE
                btnShareWhatsApp?.visibility = View.VISIBLE
                if (!parsed.vocabText.isNullOrBlank()) {
                    tvVocabContent?.text = parsed.vocabText
                    layoutVocabulary?.visibility = View.VISIBLE
                } else {
                    layoutVocabulary?.visibility = View.GONE
                }
            }.onFailure { error ->
                btnCopyClipboard?.visibility = View.GONE
                btnShareWhatsApp?.visibility = View.GONE
                layoutVocabulary?.visibility = View.GONE
                tvOutputText?.text = "Error: ${error.localizedMessage ?: "Failed to decode message."}"
            }
        }
    }

    /**
     * Action 2: Triggers Google Voice Dialog to capture Hinglish notes and convert to Shift Report.
     */
    private fun handleDictateAction() {
        val apiKey = app.getApiKey()
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
            showCard(
                modeTitle = "Setup Required",
                inputPreview = "Gemini API Key missing",
                initialOutput = "Please open the MillMitra app to configure your Gemini API Key.",
                showProgress = false
            )
            return
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showCard(
                modeTitle = "Microphone Permission Required",
                inputPreview = "Permission required for voice notes",
                initialOutput = "Please open the MillMitra app and tap 'Grant Mic' to allow voice shift notes.",
                showProgress = false
            )
            return
        }

        // Direct launch of isolated Google Voice dialog for 100% reliable dictation without opening MainActivity
        launchSystemVoiceInput()
    }

    /**
     * Initializes SpeechRecognizer on-demand with applicationContext to avoid Service context crashes.
     */
    private fun ensureSpeechRecognizer(): Boolean {
        if (speechRecognizer != null) return true
        return try {
            val targetContext = applicationContext ?: this
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(targetContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        tvInputPreview?.text = "Listening to voice input..."
                    }

                    override fun onBeginningOfSpeech() {
                        tvInputPreview?.text = "Capturing shift notes..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        tvInputPreview?.text = "Audio recorded. Processing with Gemini..."
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        progressBar?.visibility = View.GONE
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue. Tap 'Google Voice' below."
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech heard. Speak closer to phone or tap 'Google Voice' below."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout. Tap 'Google Voice' below to speak."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing in phone settings."
                            else -> "Voice recognition notice ($error). Tap 'Google Voice' below."
                        }
                        tvOutputText?.text = errorMsg
                        btnLaunchSystemVoice?.visibility = View.VISIBLE
                        btnCopyClipboard?.visibility = View.GONE
                        btnShareWhatsApp?.visibility = View.GONE
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull()?.trim()

                        if (!spokenText.isNullOrBlank()) {
                            processRecognizedVoiceText(spokenText)
                        } else {
                            progressBar?.visibility = View.GONE
                            tvOutputText?.text = "Could not detect clear words. Please tap 'Google Voice' below or try speaking closer."
                            btnLaunchSystemVoice?.visibility = View.VISIBLE
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = partialMatches?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            tvInputPreview?.text = "Heard: \"$partial\""
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("MillMitra", "Failed to initialize SpeechRecognizer", e)
            showCard(
                modeTitle = "Voice Notice",
                inputPreview = "Voice recognition notice",
                initialOutput = "Direct background voice recognition is restricted on this phone (${e.localizedMessage}). Tap 'Google Voice' below to dictate your shift notes!",
                showProgress = false
            )
            btnLaunchSystemVoice?.visibility = View.VISIBLE
            false
        }
    }

    /**
     * Common processing pipeline for voice text received either from internal SpeechRecognizer
     * or from the system Google Voice Dialog (VoiceInputActivity).
     */
    private fun processRecognizedVoiceText(spokenText: String) {
        (bubbleView as? com.millmitra.app.ui.view.DraggableBubbleLayout)?.cancelDim()
        tvModeBadge?.text = "Shift Report Generated"
        tvInputPreview?.visibility = View.VISIBLE
        tvInputPreview?.text = "Voice note: \"$spokenText\""
        tvOutputText?.text = "Generating clean, simple English shift report..."
        progressBar?.visibility = View.VISIBLE
        btnCopyClipboard?.visibility = View.GONE
        btnShareWhatsApp?.visibility = View.GONE
        btnLaunchSystemVoice?.visibility = View.GONE
        layoutVocabulary?.visibility = View.GONE
        layoutDecodeInput?.visibility = View.GONE
        showCardIfNotShowing()

        serviceScope.launch {
            val apiKey = app.getApiKey()
            val reportResult = repository.convertSpeechToReport(spokenText, apiKey)
            progressBar?.visibility = View.GONE
            reportResult.onSuccess { report ->
                val parsed = TextSanitizer.splitReportAndVocab(report)
                tvOutputText?.text = parsed.reportText
                btnCopyClipboard?.text = getString(R.string.copy_to_clipboard)
                btnCopyClipboard?.visibility = View.VISIBLE
                btnShareWhatsApp?.visibility = View.VISIBLE
                btnLaunchSystemVoice?.text = "🎙️ Speak Again (फिर से बोलें)"
                btnLaunchSystemVoice?.visibility = View.VISIBLE
                if (!parsed.vocabText.isNullOrBlank()) {
                    tvVocabContent?.text = parsed.vocabText
                    layoutVocabulary?.visibility = View.VISIBLE
                } else {
                    layoutVocabulary?.visibility = View.GONE
                }
            }.onFailure { error ->
                btnCopyClipboard?.visibility = View.GONE
                btnShareWhatsApp?.visibility = View.GONE
                layoutVocabulary?.visibility = View.GONE
                tvOutputText?.text = "Error: ${error.localizedMessage ?: "Failed to generate report."}"
                btnLaunchSystemVoice?.text = "🎙️ Try Speaking Again"
                btnLaunchSystemVoice?.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Launches the transparent VoiceInputActivity which invokes the official Google Voice Dialog.
     * Runs in an isolated task so MainActivity is NEVER brought to the foreground.
     */
    private fun launchSystemVoiceInput() {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isListening = false
        }
        val intent = Intent(this, VoiceInputActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("MillMitra", "Failed to launch VoiceInputActivity", e)
            Toast.makeText(this, "Could not open voice input: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCardIfNotShowing() {
        cardView?.let { view ->
            view.visibility = View.VISIBLE
            if (!isCardShowing || view.parent == null) {
                try {
                    if (view.parent != null) {
                        windowManager.removeView(view)
                    }
                    windowManager.addView(view, cardParams)
                    isCardShowing = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    windowManager.updateViewLayout(view, cardParams)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun showCard(
        modeTitle: String,
        inputPreview: String,
        initialOutput: String,
        showProgress: Boolean,
        isDecodeMode: Boolean = false
    ) {
        (bubbleView as? com.millmitra.app.ui.view.DraggableBubbleLayout)?.cancelDim()
        tvModeBadge?.text = modeTitle
        tvInputPreview?.text = inputPreview
        tvOutputText?.text = initialOutput
        layoutVocabulary?.visibility = View.GONE
        tvVocabContent?.text = ""
        progressBar?.visibility = if (showProgress) View.VISIBLE else View.GONE

        if (isDecodeMode) {
            layoutDecodeInput?.visibility = View.VISIBLE
            tvInputPreview?.visibility = View.GONE
            btnCopyClipboard?.visibility = View.GONE
            btnShareWhatsApp?.visibility = View.GONE
            btnLaunchSystemVoice?.visibility = View.GONE
            cardParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            cardParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        } else {
            layoutDecodeInput?.visibility = View.GONE
            tvInputPreview?.visibility = View.VISIBLE
            val hasResult = modeTitle == "Shift Report Generated" || modeTitle.contains("Decode", ignoreCase = true)
            btnCopyClipboard?.text = getString(R.string.copy_to_clipboard)
            btnCopyClipboard?.visibility = if (hasResult) View.VISIBLE else View.GONE
            btnShareWhatsApp?.visibility = if (hasResult) View.VISIBLE else View.GONE
            btnLaunchSystemVoice?.visibility = if (!hasResult && modeTitle == "MillMitra Assistant") View.VISIBLE else View.GONE
            if (btnLaunchSystemVoice?.visibility == View.VISIBLE) {
                btnLaunchSystemVoice?.text = "🎙️ Tap to Speak (Voice Note)"
            }
            cardParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            cardParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        }

        cardView?.let { view ->
            view.visibility = View.VISIBLE
            if (!isCardShowing || view.parent == null) {
                try {
                    if (view.parent != null) {
                        windowManager.removeView(view)
                    }
                    windowManager.addView(view, cardParams)
                    isCardShowing = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    windowManager.updateViewLayout(view, cardParams)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun hideCard() {
        (bubbleView as? com.millmitra.app.ui.view.DraggableBubbleLayout)?.scheduleDim(2500L)
        hideKeyboard()
        cardView?.let { view ->
            if (isCardShowing && view.parent != null) {
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            view.visibility = View.GONE
        }
        isCardShowing = false

        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isListening = false
        }
    }

    private fun hideKeyboard() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            etInputText?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleCardVisibility() {
        if (isCardShowing) {
            hideCard()
        } else {
            showCard(
                modeTitle = "MillMitra Assistant",
                inputPreview = "Ready for mill actions",
                initialOutput = "• Tap Translate to decode English messages to Hindi.\n• Tap Mic to record Hinglish shift notes and generate report.",
                showProgress = false,
                isDecodeMode = false
            )
        }
    }

    private fun startForegroundServiceWithNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, MillMitraOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MillMitra Assistant Active")
            .setContentText("Tap 'Turn Off' to close floating assistant")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_close, "Turn Off", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("MillMitra", "startForeground with microphone type failed, falling back", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e2: Exception) {
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e3: Exception) {
                    android.util.Log.e("MillMitra", "startForeground fallback failed", e3)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps MillMitra floating overlay running silently"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isCardShowing = false
        serviceScope.cancel()

        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        bubbleView?.let {
            try {
                if (it.parent != null) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        cardView?.let {
            try {
                if (it.parent != null) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "millmitra_overlay_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_STOP_SERVICE = "com.millmitra.app.ACTION_STOP_SERVICE"
        const val ACTION_PROCESS_VOICE_TEXT = "com.millmitra.app.ACTION_PROCESS_VOICE_TEXT"
        const val EXTRA_VOICE_TEXT = "extra_voice_text"

        @Volatile
        var isRunning = false
    }
}
