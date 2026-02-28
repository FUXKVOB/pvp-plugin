package com.pvpkits.spectator

import com.pvpkits.PvPKitsPlugin
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*

class SpectatorManager(private val plugin: PvPKitsPlugin) {
    
    private val spectators = mutableMapOf<UUID, SpectatorSession>()
    private val spectating = mutableMapOf<UUID, MutableSet<UUID>>() // target -> spectators
    
    /**
     * Start spectating a player
     */
    fun startSpectating(spectator: Player, target: Player): Boolean {
        if (spectator.uniqueId == target.uniqueId) return false
        if (isSpectating(spectator)) return false
        
        // Save original state
        val session = SpectatorSession(
            originalLocation = spectator.location.clone(),
            originalGameMode = spectator.gameMode,
            originalInventory = spectator.inventory.contents.clone(),
            originalArmor = spectator.inventory.armorContents.clone(),
            originalHealth = spectator.health,
            originalFoodLevel = spectator.foodLevel,
            targetUUID = target.uniqueId
        )
        
        spectators[spectator.uniqueId] = session
        spectating.getOrPut(target.uniqueId) { mutableSetOf() }.add(spectator.uniqueId)
        
        // Apply spectator mode
        spectator.gameMode = GameMode.SPECTATOR
        spectator.teleport(target.location)
        spectator.spectatorTarget = target
        
        // Add invisibility and night vision
        spectator.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, Int.MAX_VALUE, 0, false, false))
        spectator.addPotionEffect(PotionEffect(PotionEffectType.NIGHT_VISION, Int.MAX_VALUE, 0, false, false))
        
        plugin.logger.info("${spectator.name} started spectating ${target.name}")
        return true
    }
    
    /**
     * Stop spectating
     */
    fun stopSpectating(spectator: Player): Boolean {
        val session = spectators.remove(spectator.uniqueId) ?: return false
        
        // Remove from spectating map
        spectating[session.targetUUID]?.remove(spectator.uniqueId)
        if (spectating[session.targetUUID]?.isEmpty() == true) {
            spectating.remove(session.targetUUID)
        }
        
        // Restore original state
        spectator.gameMode = session.originalGameMode
        spectator.teleport(session.originalLocation)
        spectator.inventory.contents = session.originalInventory
        spectator.inventory.armorContents = session.originalArmor
        spectator.health = session.originalHealth
        spectator.foodLevel = session.originalFoodLevel
        spectator.spectatorTarget = null
        
        // Remove effects
        spectator.removePotionEffect(PotionEffectType.INVISIBILITY)
        spectator.removePotionEffect(PotionEffectType.NIGHT_VISION)
        
        plugin.logger.info("${spectator.name} stopped spectating")
        return true
    }
    
    /**
     * Check if player is spectating
     */
    fun isSpectating(player: Player): Boolean {
        return spectators.containsKey(player.uniqueId)
    }
    
    /**
     * Get spectator target
     */
    fun getSpectatorTarget(spectator: Player): UUID? {
        return spectators[spectator.uniqueId]?.targetUUID
    }
    
    /**
     * Get all spectators of a target
     */
    fun getSpectators(target: Player): Set<UUID> {
        return spectating[target.uniqueId]?.toSet() ?: emptySet()
    }
    
    /**
     * Get spectator count for a target
     */
    fun getSpectatorCount(target: Player): Int {
        return spectating[target.uniqueId]?.size ?: 0
    }
    
    /**
     * Handle target death - move spectators to killer or stop
     */
    fun handleTargetDeath(target: Player, killer: Player?) {
        val spectatorUUIDs = spectating[target.uniqueId]?.toList() ?: return
        
        spectatorUUIDs.forEach { uuid ->
            val spectator = plugin.server.getPlayer(uuid)
            if (spectator != null) {
                if (killer != null && killer.isOnline) {
                    // Switch to killer
                    stopSpectating(spectator)
                    startSpectating(spectator, killer)
                } else {
                    // Stop spectating
                    stopSpectating(spectator)
                }
            }
        }
    }
    
    /**
     * Handle target quit - stop all spectators
     */
    fun handleTargetQuit(target: Player) {
        val spectatorUUIDs = spectating[target.uniqueId]?.toList() ?: return
        
        spectatorUUIDs.forEach { uuid ->
            val spectator = plugin.server.getPlayer(uuid)
            spectator?.let { stopSpectating(it) }
        }
        
        spectating.remove(target.uniqueId)
    }
    
    /**
     * Cleanup player data
     */
    fun cleanupPlayer(uuid: UUID) {
        spectators.remove(uuid)
        spectating.remove(uuid)
    }
    
    /**
     * Get memory stats
     */
    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "active_spectators" to spectators.size,
            "targets_being_watched" to spectating.size
        )
    }
}
