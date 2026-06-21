package com.skipvox.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class VoiceSkipController(
    private val context: Context,
    private val onSkipCommandDetected: () -> Unit
) : RecognitionListener {

    companion object {
        private const val TAG = "VoiceSkipController"
        private const val RESTART_DELAY_MS = 500L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListeningActive = false

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // Keep listening as long as possible
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
    }

    fun start() {
        if (isListeningActive) return
        isListeningActive = true
        Log.d(TAG, "Starting continuous voice recognition...")
        initializeAndListen()
    }

    fun stop() {
        Log.d(TAG, "Stopping voice recognition...")
        isListeningActive = false
        handler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error while stopping speech recognizer", e)
        } finally {
            speechRecognizer = null
            SkipVoxState.setListening(false)
        }
    }

    private fun initializeAndListen() {
        if (!isListeningActive) return

        handler.post {
            try {
                speechRecognizer?.destroy()
                
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(this@VoiceSkipController)
                        startListening(recognizerIntent)
                    }
                    SkipVoxState.setListening(true)
                    Log.d(TAG, "SpeechRecognizer started successfully")
                } else {
                    Log.e(TAG, "Speech recognition is NOT available on this device")
                    SkipVoxState.setStatus("Speech recognizer unavailable")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
                scheduleRestart()
            }
        }
    }

    private fun scheduleRestart() {
        if (!isListeningActive) return
        Log.d(TAG, "Scheduling SpeechRecognizer restart in ${RESTART_DELAY_MS}ms...")
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isListeningActive) {
                initializeAndListen()
            }
        }, RESTART_DELAY_MS)
    }

    private fun processSpeechText(text: String) {
        val lowercaseText = text.lowercase(Locale.ROOT)
        Log.d(TAG, "Processed text: \"$lowercaseText\"")
        
        // Match "skip", "skip ad", "skip hulu", "skip advertisement"
        if (lowercaseText.contains("skip") || lowercaseText.contains("ad")) {
            Log.i(TAG, "Match found for skip command!")
            onSkipCommandDetected()
        }
    }

    // --- RecognitionListener Implementation ---

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "onReadyForSpeech")
        SkipVoxState.setListening(true)
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech")
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Can be used for audio volume level feedback if needed
    }

    override fun onBufferReceived(buffer: ByteArray?) {
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech")
        SkipVoxState.setListening(false)
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (no input)"
            else -> "Unknown error ($error)"
        }
        
        Log.w(TAG, "SpeechRecognizer error: $errorMessage (code $error)")
        
        // No match or speech timeout are standard events, we should just restart silently
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            scheduleRestart()
        } else {
            // For other more severe errors, recreate the recognizer after a slight delay
            scheduleRestart()
        }
    }

    override fun onResults(results: Bundle?) {
        Log.d(TAG, "onResults")
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text ->
            Log.d(TAG, "Final result: $text")
            processSpeechText(text)
        }
        // Continuous listening: restart after results
        scheduleRestart()
    }

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { text ->
            Log.d(TAG, "Partial result: $text")
            processSpeechText(text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
    }
}
