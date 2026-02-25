package com.pvpkits.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object TextUtils {
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    
    /**
     * Parse MiniMessage string to Component
     * Supports: <gradient>, <rainbow>, <color>, <hover>, <click>, etc.
     */
    fun parse(text: String): Component {
        return miniMessage.deserialize(text)
    }
    
    /**
     * Parse legacy color codes (&c, &l, etc.) to Component
     * For backward compatibility with old configs
     */
    fun parseLegacy(text: String): Component {
        return legacySerializer.deserialize(text)
    }
    
    /**
     * Smart parser: tries MiniMessage first, falls back to legacy
     */
    fun parseAuto(text: String): Component {
        return if (text.contains('<') && text.contains('>')) {
            parse(text)
        } else {
            parseLegacy(text)
        }
    }
    
    /**
     * Format time in human-readable format
     */
    fun formatTime(seconds: Long): String {
        return when {
            seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
}
