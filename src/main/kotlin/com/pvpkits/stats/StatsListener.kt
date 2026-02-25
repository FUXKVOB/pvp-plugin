package com.pvpkits.stats

import com.pvpkits.PvPKitsPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Listener for tracking player kills and deaths
 */
class StatsListener(private val plugin: PvPKitsPlugin) : Listener {
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val victim = event.entity
        val killer = victim.killer
        
        // Check if stats tracking is enabled
        if (!plugin.config.getBoolean("stats.enabled", true)) return
        
        // Check if player is in a tracked world (optional)
        val trackedWorlds = plugin.config.getStringList("stats.tracked-worlds")
        if (trackedWorlds.isNotEmpty() && victim.world.name !in trackedWorlds) return
        
        if (killer != null && killer is Player && killer.uniqueId != victim.uniqueId) {
            // Player was killed by another player
            plugin.statsManager.recordKill(
                killer.uniqueId, 
                killer.name, 
                victim.uniqueId, 
                victim.name
            )
            
            // Send kill message to killer
            if (plugin.config.getBoolean("stats.show-kill-message", true)) {
                val killerStats = plugin.statsManager.getStats(killer.uniqueId, killer.name)
                killer.sendMessage(
                    "§a§l+1 Kill §7(KD: ${killerStats.formattedKd} | Streak: ${killerStats.currentKillstreak})"
                )
            }
        } else {
            // Environmental death or suicide
            plugin.statsManager.recordDeath(victim.uniqueId, victim.name)
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Pre-load player stats
        if (plugin.config.getBoolean("stats.enabled", true)) {
            plugin.statsManager.getStats(event.player.uniqueId, event.player.name)
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // Stats are saved periodically, just cleanup memory if configured
        if (plugin.config.getBoolean("stats.cleanup-on-quit", false)) {
            plugin.statsManager.cleanupPlayer(event.player.uniqueId)
        }
    }
}
