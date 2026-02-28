package com.pvpkits.spectator

import com.pvpkits.PvPKitsPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerQuitEvent

class SpectatorListener(private val plugin: PvPKitsPlugin) : Listener {
    
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer
        
        // Handle spectators watching the victim
        plugin.spectatorManager.handleTargetDeath(victim, killer)
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        
        // If player is spectating, stop
        if (plugin.spectatorManager.isSpectating(player)) {
            plugin.spectatorManager.stopSpectating(player)
        }
        
        // If player is being spectated, stop all spectators
        plugin.spectatorManager.handleTargetQuit(player)
        
        // Cleanup
        plugin.spectatorManager.cleanupPlayer(player.uniqueId)
    }
}
