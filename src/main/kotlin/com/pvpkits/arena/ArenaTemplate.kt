package com.pvpkits.arena

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import java.io.File

/**
 * Arena template - master copy that gets cloned for matches
 */
data class ArenaTemplate(
    val name: String,
    val displayName: String,
    val worldName: String,
    val spawn1: Location,
    val spawn2: Location,
    val minBounds: Location,
    val maxBounds: Location,
    val enabled: Boolean = true,
    val allowedKits: List<String> = emptyList() // Empty = all kits allowed
) {
    
    /**
     * Check if kit is allowed in this arena
     */
    fun isKitAllowed(kitName: String): Boolean {
        if (allowedKits.isEmpty()) return true
        return allowedKits.contains(kitName.lowercase())
    }
    
    /**
     * Get arena bounds size
     */
    fun getBoundsSize(): Triple<Int, Int, Int> {
        val dx = (maxBounds.blockX - minBounds.blockX).coerceAtLeast(0)
        val dy = (maxBounds.blockY - minBounds.blockY).coerceAtLeast(0)
        val dz = (maxBounds.blockZ - minBounds.blockZ).coerceAtLeast(0)
        return Triple(dx, dy, dz)
    }
    
    /**
     * Save to config
     */
    fun saveToConfig(section: ConfigurationSection) {
        section.set("display-name", displayName)
        section.set("world", worldName)
        section.set("enabled", enabled)
        
        section.set("spawn1.x", spawn1.x)
        section.set("spawn1.y", spawn1.y)
        section.set("spawn1.z", spawn1.z)
        section.set("spawn1.yaw", spawn1.yaw)
        section.set("spawn1.pitch", spawn1.pitch)
        
        section.set("spawn2.x", spawn2.x)
        section.set("spawn2.y", spawn2.y)
        section.set("spawn2.z", spawn2.z)
        section.set("spawn2.yaw", spawn2.yaw)
        section.set("spawn2.pitch", spawn2.pitch)
        
        section.set("bounds.min.x", minBounds.blockX)
        section.set("bounds.min.y", minBounds.blockY)
        section.set("bounds.min.z", minBounds.blockZ)
        
        section.set("bounds.max.x", maxBounds.blockX)
        section.set("bounds.max.y", maxBounds.blockY)
        section.set("bounds.max.z", maxBounds.blockZ)
        
        if (allowedKits.isNotEmpty()) {
            section.set("allowed-kits", allowedKits)
        }
    }
    
    companion object {
        /**
         * Load from config
         */
        fun loadFromConfig(name: String, section: ConfigurationSection, world: World): ArenaTemplate? {
            try {
                val displayName = section.getString("display-name") ?: name
                val worldName = section.getString("world") ?: return null
                val enabled = section.getBoolean("enabled", true)
                
                val spawn1 = Location(
                    world,
                    section.getDouble("spawn1.x"),
                    section.getDouble("spawn1.y"),
                    section.getDouble("spawn1.z"),
                    section.getDouble("spawn1.yaw", 0.0).toFloat(),
                    section.getDouble("spawn1.pitch", 0.0).toFloat()
                )
                
                val spawn2 = Location(
                    world,
                    section.getDouble("spawn2.x"),
                    section.getDouble("spawn2.y"),
                    section.getDouble("spawn2.z"),
                    section.getDouble("spawn2.yaw", 0.0).toFloat(),
                    section.getDouble("spawn2.pitch", 0.0).toFloat()
                )
                
                val minBounds = Location(
                    world,
                    section.getInt("bounds.min.x").toDouble(),
                    section.getInt("bounds.min.y").toDouble(),
                    section.getInt("bounds.min.z").toDouble()
                )
                
                val maxBounds = Location(
                    world,
                    section.getInt("bounds.max.x").toDouble(),
                    section.getInt("bounds.max.y").toDouble(),
                    section.getInt("bounds.max.z").toDouble()
                )
                
                val allowedKits = section.getStringList("allowed-kits")
                
                return ArenaTemplate(
                    name, displayName, worldName,
                    spawn1, spawn2, minBounds, maxBounds,
                    enabled, allowedKits
                )
            } catch (e: Exception) {
                return null
            }
        }
    }
}

/**
 * Active arena instance - gets reset after each match
 */
data class ArenaInstance(
    val template: ArenaTemplate,
    val instanceId: String,
    var inUse: Boolean = false,
    var lastUsed: Long = 0,
    var matchCount: Int = 0
) {
    
    /**
     * Mark as in use
     */
    fun markInUse() {
        inUse = true
        lastUsed = System.currentTimeMillis()
        matchCount++
    }
    
    /**
     * Mark as free
     */
    fun markFree() {
        inUse = false
    }
    
    /**
     * Check if needs reset (after X matches or Y time)
     */
    fun needsReset(maxMatches: Int = 10, maxAge: Long = 3600000): Boolean {
        if (matchCount >= maxMatches) return true
        if (System.currentTimeMillis() - lastUsed > maxAge) return true
        return false
    }
    
    /**
     * Reset arena state
     */
    fun reset() {
        matchCount = 0
        lastUsed = System.currentTimeMillis()
    }
}
