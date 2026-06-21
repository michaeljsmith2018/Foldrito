package com.skipvox.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SkipVoxState {
    private const val TAG = "SkipVoxState"
    private const val PREFS_NAME = "skipvox_preferences"
    private const val KEY_IS_PREMIUM = "key_is_premium"
    private const val KEY_FREE_SKIPS_COUNT = "key_free_skips_count"
    private const val KEY_LAST_SKIP_DATE = "key_last_skip_date"

    private lateinit var prefs: SharedPreferences

    // State flows for real-time UI binding
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _skipStatus = MutableStateFlow("Tap Start below to begin")
    val skipStatus: StateFlow<String> = _skipStatus.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _freeSkipsRemaining = MutableStateFlow(5)
    val freeSkipsRemaining: StateFlow<Int> = _freeSkipsRemaining.asStateFlow()

    const val MAX_FREE_SKIPS = 5

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isPremium.value = prefs.getBoolean(KEY_IS_PREMIUM, false)
        resetSkipsIfNewDay()
        updateRemainingSkips()
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (running) {
            _skipStatus.value = "Service Active & Ready"
        } else {
            _isListening.value = false
            _skipStatus.value = "Service Inactive"
        }
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
        if (listening) {
            _skipStatus.value = "Listening for 'Skip' command..."
        } else if (_isServiceRunning.value) {
            _skipStatus.value = "Active (Idle)"
        }
    }

    fun setStatus(status: String) {
        _skipStatus.value = status
    }

    fun setPremium(premium: Boolean) {
        _isPremium.value = premium
        prefs.edit().putBoolean(KEY_IS_PREMIUM, premium).apply()
        updateRemainingSkips()
        Log.d(TAG, "Premium status updated to: $premium")
    }

    /**
     * Attempts to consume a skip.
     * @return true if skip is allowed (either user is Premium or has free skips left), false if blocked by daily limit.
     */
    fun tryConsumeSkip(): Boolean {
        if (_isPremium.value) {
            _skipStatus.value = "Premium Skip Triggered!"
            return true
        }

        resetSkipsIfNewDay()
        val currentCount = prefs.getInt(KEY_FREE_SKIPS_COUNT, 0)
        return if (currentCount < MAX_FREE_SKIPS) {
            val newCount = currentCount + 1
            prefs.edit()
                .putInt(KEY_FREE_SKIPS_COUNT, newCount)
                .putString(KEY_LAST_SKIP_DATE, getCurrentDateString())
                .apply()
            updateRemainingSkips()
            _skipStatus.value = "Free Skip Triggered! (${MAX_FREE_SKIPS - newCount} left today)"
            true
        } else {
            _skipStatus.value = "Limit reached! Go Premium for unlimited skips."
            false
        }
    }

    private fun updateRemainingSkips() {
        if (_isPremium.value) {
            _freeSkipsRemaining.value = -1 // Indicates unlimited
        } else {
            val currentCount = prefs.getInt(KEY_FREE_SKIPS_COUNT, 0)
            _freeSkipsRemaining.value = (MAX_FREE_SKIPS - currentCount).coerceAtLeast(0)
        }
    }

    private fun resetSkipsIfNewDay() {
        val lastSkipDate = prefs.getString(KEY_LAST_SKIP_DATE, "")
        val today = getCurrentDateString()
        if (lastSkipDate != today) {
            prefs.edit()
                .putInt(KEY_FREE_SKIPS_COUNT, 0)
                .putString(KEY_LAST_SKIP_DATE, today)
                .apply()
            updateRemainingSkips()
        }
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
