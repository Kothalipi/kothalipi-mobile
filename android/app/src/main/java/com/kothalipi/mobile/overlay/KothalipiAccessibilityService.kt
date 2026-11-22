package com.kothalipi.mobile.overlay

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional Accessibility Service that lets the Floating Bubble insert dictated text
 * directly into the currently focused text input (WhatsApp, Messenger, Gmail, Notes, etc.).
 */
class KothalipiAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: KothalipiAccessibilityService? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        /**
         * Attempts to insert text into the active editable field.
         * Returns true if inserted directly, false if caller should fall back to clipboard.
         */
        fun tryInsertText(text: String): Boolean {
            return instance?.insertIntoFocusedField(text) ?: false
        }
    }

    private var lastFocusedNode: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        ) {
            val source = event.source
            if (source != null && source.isEditable) {
                lastFocusedNode = source
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        _isConnected.value = false
        lastFocusedNode = null
        super.onDestroy()
    }

    private fun insertIntoFocusedField(newText: String): Boolean {
        if (newText.isBlank()) return false

        val activeFocus = try {
            rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } catch (_: Exception) {
            null
        }
        val node = (activeFocus?.takeIf { it.isEditable }) ?: lastFocusedNode?.takeIf { it.isEditable }
            ?: return false

        // First try pasting from clipboard if field already has text so we append cleanly at cursor
        val existing = node.text?.toString().orEmpty()
        val hint = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            node.hintText?.toString().orEmpty()
        } else {
            ""
        }
        val cleanExisting = if (existing == hint) "" else existing
        val combined = if (cleanExisting.isBlank()) {
            newText
        } else {
            val sep = if (cleanExisting.endsWith(" ") || cleanExisting.endsWith("\n")) "" else " "
            "$cleanExisting$sep$newText"
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combined)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
