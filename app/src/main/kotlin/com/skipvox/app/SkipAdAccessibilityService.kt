package com.skipvox.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class SkipAdAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "SkipAdAccessibility"
        private const val PREFS_NAME = "skipvox_preferences"
        const val KEY_IS_ENABLED = "key_is_enabled"

        // Search terms for Skip button
        private val SKIP_BUTTON_KEYWORDS = listOf(
            "skip ad",
            "skip ad",
            "skip",
            "skip ad",
            "skip advertisement",
            "skipadvertisement",
            "ad skip",
            "skipad"
        )
    }

    private var voiceSkipController: VoiceSkipController? = null
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Accessibility Service onCreate")
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility Service Connected")
        SkipVoxState.setServiceRunning(true)
        
        // Initialize state if not already done
        SkipVoxState.initialize(applicationContext)

        // Manage Voice Controller based on toggle
        evaluateListeningState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // We can inspect events if we want to run background analysis,
        // but to avoid excessive log spam or lag, we keep this empty.
        // The actual action occurs on-demand when the voice command is heard.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Accessibility Service Destroyed")
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        stopVoiceRecognition()
        SkipVoxState.setServiceRunning(false)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == KEY_IS_ENABLED) {
            evaluateListeningState()
        }
    }

    private fun evaluateListeningState() {
        val isEnabled = prefs.getBoolean(KEY_IS_ENABLED, false)
        Log.d(TAG, "Evaluate listening state: isEnabled=$isEnabled")
        if (isEnabled) {
            startVoiceRecognition()
        } else {
            stopVoiceRecognition()
        }
    }

    private synchronized fun startVoiceRecognition() {
        if (voiceSkipController == null) {
            Log.d(TAG, "Initializing VoiceSkipController...")
            voiceSkipController = VoiceSkipController(applicationContext) {
                handleSkipCommand()
            }
        }
        voiceSkipController?.start()
    }

    private synchronized fun stopVoiceRecognition() {
        Log.d(TAG, "Stopping VoiceSkipController...")
        voiceSkipController?.stop()
        voiceSkipController = null
    }

    private fun handleSkipCommand() {
        Log.i(TAG, "Skip command received. Scanning active window hierarchy...")
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "Root node in active window is null. Cannot scan hierarchy.")
            SkipVoxState.setStatus("Heard 'Skip', but active window is inaccessible.")
            return
        }

        val skipButton = findSkipButtonNode(rootNode)
        if (skipButton != null) {
            Log.i(TAG, "Found Skip Ad button!")
            
            // Try consuming daily limit/check premium
            if (SkipVoxState.tryConsumeSkip()) {
                val clicked = performClick(skipButton)
                if (clicked) {
                    Log.i(TAG, "Successfully clicked Skip Ad button!")
                } else {
                    Log.e(TAG, "Failed to perform click on Skip Ad button.")
                    SkipVoxState.setStatus("Found skip button, but click failed.")
                }
            } else {
                Log.w(TAG, "Skip blocked due to daily limit.")
            }
            skipButton.recycle()
        } else {
            Log.i(TAG, "No Skip Ad button found in current view hierarchy.")
            SkipVoxState.setStatus("Heard 'Skip', but no button detected on screen.")
        }
        rootNode.recycle()
    }

    /**
     * Recursively searches for an AccessibilityNodeInfo that resembles a skip button.
     */
    private fun findSkipButtonNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        // Check text
        val text = node.text?.toString()?.lowercase(Locale.ROOT)
        if (text != null && isSkipTextMatch(text)) {
            Log.d(TAG, "Matched text: \"$text\" in node class: ${node.className}")
            return AccessibilityNodeInfo.obtain(node)
        }

        // Check content description
        val contentDesc = node.contentDescription?.toString()?.lowercase(Locale.ROOT)
        if (contentDesc != null && isSkipTextMatch(contentDesc)) {
            Log.d(TAG, "Matched contentDescription: \"$contentDesc\" in node class: ${node.className}")
            return AccessibilityNodeInfo.obtain(node)
        }

        // Check view ID for streaming-specific selectors (e.g. YouTube's skip button)
        val viewId = node.viewIdResourceName
        if (viewId != null && viewId.contains("skip-button", ignoreCase = true)) {
            Log.d(TAG, "Matched view ID resource name: \"$viewId\" in node class: ${node.className}")
            return AccessibilityNodeInfo.obtain(node)
        }

        // Recursively search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findSkipButtonNode(child)
            child?.recycle()
            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun isSkipTextMatch(text: String): Boolean {
        return SKIP_BUTTON_KEYWORDS.any { keyword ->
            text.contains(keyword)
        }
    }

    /**
     * Attempts to click the node, walking up parents if the target node is not clickable.
     */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var tempNode = node
        while (tempNode != null) {
            if (tempNode.isClickable) {
                Log.d(TAG, "Performing ACTION_CLICK on clickable node: ${tempNode.className}")
                val success = tempNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    return true
                }
            }
            val parent = tempNode.parent
            if (tempNode != node) {
                tempNode.recycle() // Recycle temporary nodes to prevent memory leaks
            }
            tempNode = parent
        }
        return false
    }
}
