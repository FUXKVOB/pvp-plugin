package com.pvpkits.duel

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.SchedulerUtils
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages duel queues and matches
 */
class DuelManager(private val plugin: PvPKitsPlugin) {
    
    // Queues per kit
    private val queues = ConcurrentHashMap<String, MutableList<DuelQueueEntry>>()
    
    // Active matches
    private val matches = ConcurrentHashMap<String, DuelMatch>()
    
    // Player -> Match mapping
    private val playerMatches = ConcurrentHashMap<UUID, DuelMatch>()
    
    // Player -> Queue mapping
    private val playerQueues = ConcurrentHashMap<UUID, String>()
    
    // Countdown tasks
    private val countdownTasks = ConcurrentHashMap<String, org.bukkit.scheduler.BukkitTask>()
    
    // Duel arena spawns (can be configured)
    private val duelSpawns = mutableListOf<Pair<Location, Location>>()
    
    companion object {
        private const val COUNTDOWN_SECONDS = 5
        private const val ROUND_END_DELAY = 3 // seconds before next round
    }
    
    /**
     * Initialize duel spawns from arena worlds
     */
    fun initializeSpawns() {
        // Use WorldManager to get arena spawns
        val arenaData = plugin.worldManager.getRandomArenaWithSpawns()
        
        if (arenaData != null) {
            val (arenaName, spawns) = arenaData
            duelSpawns.add(spawns)
            plugin.logger.info("Loaded duel arena: $arenaName")
        } else {
            // Fallback: use default world spawn area for duels
            val world = Bukkit.getWorlds().firstOrNull() ?: return
            val baseLoc = world.spawnLocation
            
            // Create 8 duel arenas as fallback
            for (i in 0 until 8) {
                val offset = i * 100
                duelSpawns.add(
                    Location(world, baseLoc.x + offset, baseLoc.y + 10.0, baseLoc.z) to
                    Location(world, baseLoc.x + offset + 20, baseLoc.y + 10.0, baseLoc.z + 20)
                )
            }
            plugin.logger.warning("No arena worlds found, using fallback spawns")
        }
    }
    
    /**
     * Add player to queue for a kit
     */
    fun joinQueue(player: Player, kitName: String): Boolean {
        val uuid = player.uniqueId
        
        // Check if already in queue or match
        if (isInQueue(uuid) || isInMatch(uuid)) {
            player.sendMessage("${ChatColor.RED}You are already in a queue or match!")
            return false
        }
        
        // Check if kit exists
        if (plugin.kitManager.getKit(kitName) == null) {
            player.sendMessage("${ChatColor.RED}Kit '$kitName' not found!")
            return false
        }
        
        val kitKey = kitName.lowercase()
        val queue = queues.getOrPut(kitKey) { mutableListOf() }
        
        // Check if someone is waiting
        if (queue.isNotEmpty()) {
            val opponent = queue.removeAt(0)
            playerQueues.remove(opponent.uuid)
            
            // Start match!
            startMatch(player, opponent, kitName)
            return true
        }
        
        // Add to queue
        queue.add(DuelQueueEntry(uuid, player.name, kitName))
        playerQueues[uuid] = kitKey
        
        player.sendMessage("")
        player.sendMessage("${ChatColor.GREEN}═════════════════════════════")
        player.sendMessage("${ChatColor.GOLD}   Joined ${ChatColor.YELLOW}$kitName ${ChatColor.GOLD}Queue")
        player.sendMessage("${ChatColor.GRAY}   Waiting for opponent...")
        player.sendMessage("${ChatColor.GREEN}═════════════════════════════")
        player.sendMessage("")
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f)
        
        return true
    }
    
    /**
     * Remove player from queue
     */
    fun leaveQueue(player: Player): Boolean {
        val uuid = player.uniqueId
        val kitKey = playerQueues.remove(uuid) ?: return false
        
        queues[kitKey]?.removeIf { it.uuid == uuid }
        
        player.sendMessage("${ChatColor.YELLOW}Left the queue.")
        return true
    }
    
    /**
     * Check if player is in queue
     */
    fun isInQueue(uuid: UUID): Boolean = playerQueues.containsKey(uuid)
    
    /**
     * Check if player is in match
     */
    fun isInMatch(uuid: UUID): Boolean = playerMatches.containsKey(uuid)
    
    /**
     * Get player's current match
     */
    fun getPlayerMatch(uuid: UUID): DuelMatch? = playerMatches[uuid]
    
    /**
     * Get queue size for a kit
     */
    fun getQueueSize(kitName: String): Int = queues[kitName.lowercase()]?.size ?: 0
    
    /**
     * Get total players in all queues
     */
    fun getTotalInQueues(): Int = playerQueues.size
    
    /**
     * Start a duel match between two players
     */
    private fun startMatch(player1: Player, entry2: DuelQueueEntry, kitName: String) {
        val player2 = Bukkit.getPlayer(entry2.uuid) ?: return
        startDirectDuel(player1, player2, kitName)
    }
    
    /**
     * Start direct duel between two players (for challenges)
     */
    fun startDirectDuel(player1: Player, player2: Player, kitName: String) {
        // Try to get arena instance from improved arena manager
        val arenaInstance = plugin.improvedArenaManager.getAvailableInstance(kitName)
        
        if (arenaInstance != null) {
            // Use improved arena system
            val matchId = "${player1.uniqueId}_${System.currentTimeMillis()}"
            
            val match = DuelMatch(
                id = matchId,
                player1 = player1.uniqueId,
                player2 = player2.uniqueId,
                kitName = kitName,
                spawn1 = arenaInstance.template.spawn1,
                spawn2 = arenaInstance.template.spawn2
            )
            
            matches[matchId] = match
            playerMatches[player1.uniqueId] = match
            playerMatches[player2.uniqueId] = match
            
            // Start match in arena instance
            plugin.improvedArenaManager.startMatch(arenaInstance, player1, player2)
            
            // Notify players
            notifyMatchFound(player1, player2, kitName)
            
            // Start countdown
            startCountdown(match)
            return
        }
        
        // Fallback to old system
        var spawns = plugin.worldManager.getRandomArenaWithSpawns()?.second
        
        if (spawns == null) {
            spawns = duelSpawns.firstOrNull()
        }
        
        if (spawns == null) {
            player1.sendMessage("${ChatColor.RED}No arena available!")
            player2.sendMessage("${ChatColor.RED}No arena available!")
            return
        }
        
        val matchId = "${player1.uniqueId}_${System.currentTimeMillis()}"
        
        val match = DuelMatch(
            id = matchId,
            player1 = player1.uniqueId,
            player2 = player2.uniqueId,
            kitName = kitName,
            spawn1 = spawns.first,
            spawn2 = spawns.second
        )
        
        matches[matchId] = match
        playerMatches[player1.uniqueId] = match
        playerMatches[player2.uniqueId] = match
        
        // Notify players
        notifyMatchFound(player1, player2, kitName)
        
        // Start countdown
        startCountdown(match)
    }
    
    /**
     * Notify players that match was found
     */
    private fun notifyMatchFound(player1: Player, player2: Player, kitName: String) {
        val message = buildString {
            append("${ChatColor.GREEN}═════════════════════════════\n")
            append("${ChatColor.GOLD}   ⚔ MATCH FOUND! ⚔\n")
            append("${ChatColor.YELLOW}   Kit: ${ChatColor.WHITE}$kitName\n")
            append("${ChatColor.YELLOW}   Opponent: ${ChatColor.WHITE}${player1.name} vs ${player2.name}\n")
            append("${ChatColor.GREEN}═════════════════════════════")
        }
        
        player1.sendMessage(message)
        player2.sendMessage(message)
        
        player1.playSound(player1.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        player2.playSound(player2.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
    }
    
    /**
     * Start countdown before round
     */
    private fun startCountdown(match: DuelMatch) {
        match.state = DuelState.COUNTDOWN
        
        val player1 = Bukkit.getPlayer(match.player1) ?: return
        val player2 = Bukkit.getPlayer(match.player2) ?: return
        
        // Teleport and prepare players
        preparePlayerForRound(player1, match)
        preparePlayerForRound(player2, match)
        
        player1.teleport(match.spawn1)
        player2.teleport(match.spawn2)
        
        // Give kit
        plugin.kitManager.giveKit(player1, match.kitName)
        plugin.kitManager.giveKit(player2, match.kitName)
        
        // Countdown task using Folia-compatible scheduler
        var secondsLeft = COUNTDOWN_SECONDS
        
        val task = SchedulerUtils.runTaskTimer(plugin, 0L, 20L, Runnable {
            if (secondsLeft <= 0) {
                startRound(match)
                countdownTasks.remove(match.id)?.cancel()
                return@Runnable
            }
            
            // Send countdown
            val title = when (secondsLeft) {
                5 -> "§e5"
                4 -> "§e4"
                3 -> "§c3"
                2 -> "§c2"
                1 -> "§4§l1"
                else -> "§f$secondsLeft"
            }
            
            player1.sendTitle(title, "§7Round ${match.currentRound}", 0, 25, 0)
            player2.sendTitle(title, "§7Round ${match.currentRound}", 0, 25, 0)
            
            player1.playSound(player1.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f)
            player2.playSound(player2.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f)
            
            secondsLeft--
        })
        
        countdownTasks[match.id] = task
    }
    
    /**
     * Prepare player for round
     */
    private fun preparePlayerForRound(player: Player, match: DuelMatch) {
        player.gameMode = GameMode.ADVENTURE
        player.health = player.maxHealth
        player.foodLevel = 20
        player.saturation = 20f
        player.fireTicks = 0
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
    }
    
    /**
     * Start a round
     */
    private fun startRound(match: DuelMatch) {
        match.state = DuelState.IN_PROGRESS
        
        val player1 = Bukkit.getPlayer(match.player1) ?: return
        val player2 = Bukkit.getPlayer(match.player2) ?: return
        
        player1.sendTitle("§a§lFIGHT!", "§7Round ${match.currentRound}", 0, 40, 10)
        player2.sendTitle("§a§lFIGHT!", "§7Round ${match.currentRound}", 0, 40, 10)
        
        player1.playSound(player1.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f)
        player2.playSound(player2.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f)
        
        broadcastToMatch(match, "${ChatColor.GREEN}§l⚔ Round ${match.currentRound} - FIGHT!")
    }
    
    /**
     * Handle player death in duel
     */
    fun handleDeath(deadPlayer: Player): Boolean {
        val match = getPlayerMatch(deadPlayer.uniqueId) ?: return false
        
        if (match.state != DuelState.IN_PROGRESS) return false
        
        match.state = DuelState.ROUND_END
        
        val winner = Bukkit.getPlayer(match.getOpponent(deadPlayer.uniqueId)) ?: return true
        val loser = deadPlayer
        
        // Record win
        match.addWin(winner.uniqueId)
        
        // Broadcast round result
        broadcastToMatch(match, "")
        broadcastToMatch(match, "${ChatColor.GOLD}═════════════════════════════")
        broadcastToMatch(match, "${ChatColor.YELLOW}${winner.name} ${ChatColor.GREEN}wins Round ${match.currentRound}!")
        broadcastToMatch(match, "${ChatColor.GRAY}Score: ${ChatColor.YELLOW}${match.player1Wins} ${ChatColor.GRAY}- ${ChatColor.YELLOW}${match.player2Wins}")
        broadcastToMatch(match, "${ChatColor.GOLD}═════════════════════════════")
        broadcastToMatch(match, "")
        
        winner.playSound(winner.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        loser.playSound(loser.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)
        
        // Check if match is over
        if (match.isMatchOver()) {
            endMatch(match)
        } else {
            // Schedule next round using Folia-compatible scheduler
            match.currentRound++
            SchedulerUtils.runTaskLater(plugin, (ROUND_END_DELAY * 20).toLong(), Runnable {
                startCountdown(match)
            })
        }
        
        return true
    }
    
    /**
     * End a match
     */
    private fun endMatch(match: DuelMatch) {
        match.state = DuelState.MATCH_END
        
        val winnerUUID = match.getWinner()
        val loserUUID = match.getLoser()
        val winner = winnerUUID?.let { Bukkit.getPlayer(it) }
        val loser = loserUUID?.let { Bukkit.getPlayer(it) }
        
        // Broadcast final result
        broadcastToMatch(match, "")
        broadcastToMatch(match, "${ChatColor.GOLD}§l═════════════════════════════")
        broadcastToMatch(match, "${ChatColor.GREEN}§l   ⚔ MATCH OVER! ⚔")
        broadcastToMatch(match, "")
        broadcastToMatch(match, "${ChatColor.YELLOW}   Winner: ${ChatColor.GREEN}§l${winner?.name ?: "Unknown"}")
        broadcastToMatch(match, "${ChatColor.GRAY}   Final Score: ${ChatColor.YELLOW}${match.player1Wins} - ${match.player2Wins}")
        broadcastToMatch(match, "${ChatColor.GOLD}§l═════════════════════════════")
        broadcastToMatch(match, "")
        
        // Play sounds
        if (winner != null) {
            winner.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        }
        if (loser != null) {
            loser.playSound(loser.location, Sound.ENTITY_WITHER_DEATH, 0.5f, 1.0f)
        }
        
        // Record stats
        if (winner != null && loserUUID != null) {
            plugin.statsManager.recordKill(winner.uniqueId, winner.name, loserUUID, loser?.name ?: "Unknown")
        }
        
        // Handle tournament match end
        if (match.isTournamentMatch) {
            handleTournamentMatchEnd(match)
        }
        
        // End arena instance if using improved arena system
        if (winner != null) {
            val arenaInstance = plugin.improvedArenaManager.getPlayerInstance(winner)
            if (arenaInstance != null) {
                plugin.improvedArenaManager.endMatch(arenaInstance)
            }
        }
        
        // Teleport players out using Folia-compatible scheduler
        SchedulerUtils.runTaskLater(plugin, (5 * 20).toLong(), Runnable {
            winner?.teleport(Bukkit.getWorlds().first().spawnLocation)
            loser?.teleport(Bukkit.getWorlds().first().spawnLocation)
            
            // Clean up
            cleanupMatch(match)
        })
    }
    
    /**
     * Broadcast message to both players in match
     */
    private fun broadcastToMatch(match: DuelMatch, message: String) {
        Bukkit.getPlayer(match.player1)?.sendMessage(message)
        Bukkit.getPlayer(match.player2)?.sendMessage(message)
    }
    
    /**
     * Clean up a finished match
     */
    private fun cleanupMatch(match: DuelMatch) {
        matches.remove(match.id)
        playerMatches.remove(match.player1)
        playerMatches.remove(match.player2)
        countdownTasks.remove(match.id)?.cancel()
    }
    
    /**
     * Force end a match (player quit, etc)
     */
    fun forceEndMatch(uuid: UUID) {
        val match = playerMatches[uuid] ?: return
        
        val opponent = match.getOpponent(uuid)
        val opponentPlayer = Bukkit.getPlayer(opponent)
        
        opponentPlayer?.sendMessage("${ChatColor.RED}Your opponent left. You win by forfeit!")
        opponentPlayer?.teleport(Bukkit.getWorlds().first().spawnLocation)
        
        cleanupMatch(match)
    }
    
    /**
     * Clean up player data
     */
    fun cleanupPlayer(uuid: UUID) {
        leaveQueue(Bukkit.getPlayer(uuid) ?: return)
        
        if (isInMatch(uuid)) {
            forceEndMatch(uuid)
        }
    }
    
    /**
     * Get active match count
     */
    fun getActiveMatchCount(): Int = matches.size
    
    /**
     * Get queue info for display
     */
    fun getQueueInfo(): String {
        return buildString {
            append("${ChatColor.GOLD}═════════════════════════════\n")
            append("${ChatColor.YELLOW}Players in queues: ${ChatColor.WHITE}${playerQueues.size}\n")
            append("${ChatColor.YELLOW}Active matches: ${ChatColor.WHITE}${matches.size}\n")
            append("${ChatColor.GOLD}═════════════════════════════")
        }
    }
    
    /**
     * Start a tournament match (called by TournamentManager)
     */
    fun startTournamentMatch(player1: Player, player2: Player, kitName: String, tournamentMatchId: String) {
        // Get available spawns
        var spawns = plugin.worldManager.getRandomArenaWithSpawns()?.second
        
        if (spawns == null) {
            spawns = duelSpawns.firstOrNull()
        }
        
        if (spawns == null) {
            plugin.logger.warning("No arena available for tournament match!")
            return
        }
        
        val matchId = "tournament_$tournamentMatchId"
        
        val match = DuelMatch(
            id = matchId,
            player1 = player1.uniqueId,
            player2 = player2.uniqueId,
            kitName = kitName,
            spawn1 = spawns.first,
            spawn2 = spawns.second,
            isTournamentMatch = true,
            tournamentMatchId = tournamentMatchId
        )
        
        matches[matchId] = match
        playerMatches[player1.uniqueId] = match
        playerMatches[player2.uniqueId] = match
        
        // Notify players
        notifyMatchFound(player1, player2, kitName)
        
        // Start countdown
        startCountdown(match)
    }
    
    /**
     * Handle tournament match end
     */
    private fun handleTournamentMatchEnd(match: DuelMatch) {
        val winnerUUID = match.getWinner() ?: return
        
        // Notify tournament manager
        match.tournamentMatchId?.let { tournamentMatchId ->
            plugin.tournamentManager.handleMatchComplete(tournamentMatchId, winnerUUID)
        }
    }
}
