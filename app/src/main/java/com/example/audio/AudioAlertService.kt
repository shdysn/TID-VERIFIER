package com.example.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class AudioAlertService(private val context: Context) : TextToSpeech.OnInitListener {

    private var toneGenerator: ToneGenerator? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    private val scope = CoroutineScope(Dispatchers.Default)
    private var sirenJob: Job? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            textToSpeech = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                val result = tts.setLanguage(Locale.US)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setPitch(1.0f)
                    tts.setSpeechRate(1.05f)
                    isTtsReady = true
                }
            }
        }
    }

    /**
     * Triggers the Emergency Siren loop with haptic vibration and urgent voice warning.
     */
    fun triggerFraudSiren(spokenMessage: String = "Warning! Fake payment alert. Transaction ID was not found in your SMS inbox!") {
        stopSiren()

        sirenJob = scope.launch {
            // First speak voice warning
            speak(spokenMessage)
            delay(1500)

            // Pulse loud siren tone
            var toggle = false
            while (isActive) {
                try {
                    val tone = if (toggle) ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK else ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
                    toneGenerator?.startTone(tone, 350)
                    triggerVibration(isUrgent = true)
                    toggle = !toggle
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(400)
            }
        }
    }

    /**
     * Plays clean verified chime and voice confirmation.
     */
    fun triggerVerifiedChime(amount: Double, providerName: String) {
        stopSiren()
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
            triggerVibration(isUrgent = false)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val speech = if (amount > 0) {
            "Payment verified. Rupees ${amount.toInt()} confirmed from $providerName."
        } else {
            "Payment verified and confirmed from $providerName."
        }
        speak(speech)
    }

    fun stopSiren() {
        sirenJob?.cancel()
        sirenJob = null
        try {
            toneGenerator?.stopTone()
        } catch (_: Exception) {}
    }

    fun isSirenActive(): Boolean {
        return sirenJob?.isActive == true
    }

    private fun speak(text: String) {
        if (isTtsReady && textToSpeech != null) {
            try {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "verifier_alert")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun triggerVibration(isUrgent: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (isUrgent) {
                    VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createOneShot(100, 150)
                }
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(if (isUrgent) 300L else 100L)
            }
        } catch (_: Exception) {}
    }

    fun release() {
        stopSiren()
        try {
            toneGenerator?.release()
            toneGenerator = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        } catch (_: Exception) {}
    }
}
