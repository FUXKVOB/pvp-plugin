package com.pvpkits.duel

import com.github.shynixn.mccoroutine.bukkit.launch
import com.pvpkits.PvPKitsPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Listener for duel-related events
 */
class DuelListener(private val plugin: PvPKitsPlugin) : Listener {
    
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        
        // Check if player is in a duel
        if (plugin.duelManager.isInMatch(player.uniqueId)) {
            // Handle duel death
            if (plugin.duelManager.handleDeath(player)) {
                // Clear death message and drops
                event.deathMessage = null
                event.drops.clear()
                event.droppedExp = 0
                
                // Auto respawn
                plugin.launch {
                    Thread.sleep(50)
                    player.spigot().respawn()
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Clean up if player was in duel
        if (plugin.duelManager.isInMatch(event.player.uniqueId)) {
            plugin.duelManager.forceEndMatch(event.player.uniqueId)
        }
        
        // Remove from queue if in queue
        if (plugin.duelManager.isInQueue(event.player.uniqueId)) {
            plugin.duelManager.leaveQueue(event.player)
        }
    }
}
