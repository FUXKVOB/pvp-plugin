package com.pvpkits.arena

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.SchedulerUtils
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the main lobby and arena queue system
 */
class LobbyManager(private val plugin: PvPKitsPlugin) {
    
    private val lobbyLocation: Location? = null
    private val queuedPlayers = ConcurrentHashMap<UUID, String>() // Player UUID -> Preferred arena (or "any")
    private val countdownTasks = ConcurrentHashMap<String, org.bukkit.scheduler.BukkitTask>() // Arena name -> Countdown task
    private val countdownSeconds = ConcurrentHashMap<String, Int>() // Arena name -> Seconds remaining
    
    companion object {
        private const val DEFAULT_COUNTDOWN = 30
        private const val MIN_PLAYERS_TO_START = 2
    }
    
    /**
     * Add player to queue for an arena
     */
    fun addToQueue(player: Player, arenaName: String = "any"): Boolean {
        if (queuedPlayers.containsKey(player.uniqueId)) {
            player.sendMessage("${ChatColor.RED}You are already in queue!")
            return false
        }
        
        queuedPlayers[player.uniqueId] = arenaName.lowercase()
        player.sendMessage("${ChatColor.GREEN}Added to queue for ${ChatColor.YELLOW}${arenaName}${ChatColor.GREEN}!")
        
        // Check if we can start a countdown
        checkAndStartCountdown(arenaName)
        
        return true
    }
    
    /**
     * Remove player from queue
     */
    fun removeFromQueue(player: Player): Boolean {
        if (!queuedPlayers.containsKey(player.uniqueId)) {
            player.sendMessage("${ChatColor.RED}You are not in queue!")
            return false
        }
        
        queuedPlayers.remove(player.uniqueId)
        player.sendMessage("${ChatColor.YELLOW}Removed from queue.")
        
        return true
    }
    
    /**
     * Check if player is in queue
     */
    fun isInQueue(player: Player): Boolean = queuedPlayers.containsKey(player.uniqueId)
    
    /**
     * Get queue size
     */
    fun getQueueSize(): Int = queuedPlayers.size
    
    /**
     * Check and start countdown for an arena
     */
    private fun checkAndStartCountdown(arenaName: String) {
        val arena = if (arenaName == "any") {
            // Find arena with most players
            plugin.arenaManager.getEnabledArenas().maxByOrNull { 
                plugin.arenaManager.getPlayerCount(it.name) 
            }
        } else {
            plugin.arenaManager.getArena(arenaName)
        } ?: return
        
        // Check if countdown already running
        if (countdownTasks.containsKey(arena.name.lowercase())) return
        
        // Count players in queue for this arena + already in arena
        val queuedForArena = queuedPlayers.values.count { 
            it == arena.name.lowercase() || it == "any" 
        }
        val inArena = plugin.arenaManager.getPlayerCount(arena.name)
        val total = queuedForArena + inArena
        
        if (total >= arena.minPlayers && total >= MIN_PLAYERS_TO_START) {
            startCountdown(arena.name)
        }
    }
    
    /**
     * Start countdown for an arena
     */
    private fun startCountdown(arenaName: String) {
        val arena = plugin.arenaManager.getArena(arenaName) ?: return
        
        countdownSeconds[arenaName.lowercase()] = DEFAULT_COUNTDOWN
        
        val task = SchedulerUtils.runTaskTimer(plugin, 0L, 20L, Runnable {
            val seconds = countdownSeconds[arenaName.lowercase()] ?: 0
            
            if (seconds <= 0) {
                // Countdown finished - start game
                startGame(arenaName)
                cancelCountdown(arenaName)
                return@Runnable
            }
            
            // Broadcast countdown
            if (seconds <= 10 || seconds % 10 == 0) {
                broadcastQueue(arenaName, "${ChatColor.GOLD}Game starting in ${ChatColor.YELLOW}$seconds ${ChatColor.GOLD}seconds!")
                
                // Play sound
                if (seconds <= 5) {
                    playSoundInQueue(arenaName, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f)
                }
            }
            
            countdownSeconds[arenaName.lowercase()] = seconds - 1
        })
        
        countdownTasks[arenaName.lowercase()] = task
    }
    
    /**
     * Cancel countdown for an arena
     */
    private fun cancelCountdown(arenaName: String) {
        countdownTasks.remove(arenaName.lowercase())?.cancel()
        countdownSeconds.remove(arenaName.lowercase())
    }
    
    /**
     * Start the game - move all queued players to arena
     */
    private fun startGame(arenaName: String) {
        val arena = plugin.arenaManager.getArena(arenaName) ?: return
        
        // Get all players queued for this arena or "any"
        val playersToMove = queuedPlayers.entries
            .filter { it.value == arenaName.lowercase() || it.value == "any" }
            .mapNotNull { Bukkit.getPlayer(it.key) }
        
        // Remove from queue and add to arena
        playersToMove.forEach { player ->
            queuedPlayers.remove(player.uniqueId)
            plugin.arenaManager.joinArena(player, arenaName)
        }
        
        // Broadcast game start
        plugin.arenaManager.broadcastInArena(arenaName, "${ChatColor.GREEN}═════════════════════════════")
        plugin.arenaManager.broadcastInArena(arenaName, "${ChatColor.GOLD}   ⚔ GAME STARTED! ⚔")
        plugin.arenaManager.broadcastInArena(arenaName, "${ChatColor.GREEN}═════════════════════════════")
        
        playSoundInArena(arenaName, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f)
    }
    
    /**
     * Broadcast message to all queued players for an arena
     */
    private fun broadcastQueue(arenaName: String, message: String) {
        queuedPlayers.entries
            .filter { it.value == arenaName.lowercase() || it.value == "any" }
            .forEach { entry ->
                Bukkit.getPlayer(entry.key)?.sendMessage(message)
            }
    }
    
    /**
     * Play sound to all queued players for an arena
     */
    private fun playSoundInQueue(arenaName: String, sound: Sound, volume: Float, pitch: Float) {
        queuedPlayers.entries
            .filter { it.value == arenaName.lowercase() || it.value == "any" }
            .forEach { entry ->
                val player = Bukkit.getPlayer(entry.key)
                player?.playSound(player.location, sound, volume, pitch)
            }
    }
    
    /**
     * Play sound to all players in an arena
     */
    private fun playSoundInArena(arenaName: String, sound: Sound, volume: Float, pitch: Float) {
        plugin.arenaManager.getPlayersInArena(arenaName).forEach { uuid ->
            val player = Bukkit.getPlayer(uuid)
            if (player != null) {
                player.playSound(player.location, sound, volume, pitch)
            }
        }
    }
    
    /**
     * Clean up player from queue
     */
    fun cleanupPlayer(uuid: UUID) {
        queuedPlayers.remove(uuid)
    }
    
    /**
     * Get lobby info
     */
    fun getLobbyInfo(): String {
        return buildString {
            append("${ChatColor.GOLD}═════════════════════════════\n")
            append("${ChatColor.YELLOW}Players in queue: ${ChatColor.WHITE}${queuedPlayers.size}\n")
            append("${ChatColor.YELLOW}Available arenas: ${ChatColor.WHITE}${plugin.arenaManager.getEnabledArenas().size}\n")
            append("${ChatColor.GOLD}═════════════════════════════")
        }
    }
}
