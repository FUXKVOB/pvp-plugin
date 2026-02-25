package com.pvpkits.stats

import java.util.UUID

/**
 * Player statistics data class
 */
data class PlayerStats(
    val uuid: UUID,
    val playerName: String,
    var kills: Int = 0,
    var deaths: Int = 0,
    var currentKillstreak: Int = 0,
    var bestKillstreak: Int = 0,
    var kitsUsed: MutableMap<String, Int> = mutableMapOf(),
    var lastKitUsed: String? = null,
    var lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Calculate K/D ratio
     */
    val kdRatio: Double
        get() = if (deaths == 0) kills.toDouble() else kills.toDouble() / deaths.toDouble()
    
    /**
     * Format K/D ratio for display
     */
    val formattedKd: String
        get() = String.format("%.2f", kdRatio)
    
    /**
     * Add a kill and update killstreak
     */
    fun addKill() {
        kills++
        currentKillstreak++
        if (currentKillstreak > bestKillstreak) {
            bestKillstreak = currentKillstreak
        }
        lastUpdated = System.currentTimeMillis()
    }
    
    /**
     * Add a death and reset killstreak
     */
    fun addDeath() {
        deaths++
        currentKillstreak = 0
        lastUpdated = System.currentTimeMillis()
    }
    
    /**
     * Record kit usage
     */
    fun useKit(kitName: String) {
        kitsUsed[kitName] = (kitsUsed[kitName] ?: 0) + 1
        lastKitUsed = kitName
        lastUpdated = System.currentTimeMillis()
    }
    
    /**
     * Get favorite kit (most used)
     */
    val favoriteKit: String?
        get() = kitsUsed.entries.maxByOrNull { it.value }?.key
    
    /**
     * Get total games played
     */
    val totalGames: Int
        get() = kills + deaths
    
    /**
     * Serialize to map for saving
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "uuid" to uuid.toString(),
            "playerName" to playerName,
            "kills" to kills,
            "deaths" to deaths,
            "currentKillstreak" to currentKillstreak,
            "bestKillstreak" to bestKillstreak,
            "kitsUsed" to kitsUsed,
            "lastKitUsed" to (lastKitUsed ?: ""),
            "lastUpdated" to lastUpdated
        )
    }
    
    companion object {
        /**
         * Deserialize from map
         */
        fun fromMap(map: Map<*, *>): PlayerStats {
            return PlayerStats(
                uuid = UUID.fromString(map["uuid"] as String),
                playerName = map["playerName"] as String,
                kills = (map["kills"] as? Number)?.toInt() ?: 0,
                deaths = (map["deaths"] as? Number)?.toInt() ?: 0,
                currentKillstreak = (map["currentKillstreak"] as? Number)?.toInt() ?: 0,
                bestKillstreak = (map["bestKillstreak"] as? Number)?.toInt() ?: 0,
                kitsUsed = (map["kitsUsed"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                    ?.mapValues { (it.value as? Number)?.toInt() ?: 0 }?.toMutableMap() ?: mutableMapOf(),
                lastKitUsed = (map["lastKitUsed"] as? String)?.takeIf { it.isNotEmpty() },
                lastUpdated = (map["lastUpdated"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
