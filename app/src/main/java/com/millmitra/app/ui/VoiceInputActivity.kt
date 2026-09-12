package com.millmitra.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.millmitra.app.service.MillMitraOverlayService

/**
 * Transparent Activity that safely hosts the system's official Google Speech Dialog.
 * Overcomes background microphone capture restrictions on Android 14 and aggressive OEM battery savers (MIUI, ColorOS, FunTouchOS).
 */
class VoiceInputActivity : AppCompatActivity() {

    private val speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()?.trim()
            if (!spokenText.isNullOrBlank()) {
                val serviceIntent = Intent(this, MillMitraOverlayService::class.java).apply {
                    action = MillMitraOverlayService.ACTION_PROCESS_VOICE_TEXT
                    putExtra(MillMitraOverlayService.EXTRA_VOICE_TEXT, spokenText)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                Toast.makeText(this, "No words heard. Please try speaking closer to the microphone.", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchGoogleVoiceDialog()
    }

    private fun launchGoogleVoiceDialog() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "hi-IN", "en-US"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak mill notes in Hinglish or Hindi...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Google Voice is not enabled or available: ", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
