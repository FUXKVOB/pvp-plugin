package com.pvpkits.scoreboard

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.duel.DuelState
import com.pvpkits.utils.SchedulerUtils
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages player scoreboards with stats display
 */
class ScoreboardManager(private val plugin: PvPKitsPlugin) {
    
    private val playerScoreboards = ConcurrentHashMap<UUID, PlayerScoreboard>()
    
    companion object {
        private const val UPDATE_INTERVAL = 20L // 1 second
        
        // Анимированные заголовки (2026 визуал)
        private val TITLE_FRAMES = listOf(
            "§c§l⚔ §6§lPvPKits §c§l⚔",
            "§6§l⚔ §e§lPvPKits §6§l⚔",
            "§e§l⚔ §f§lPvPKits §e§l⚔",
            "§f§l⚔ §e§lPvPKits §f§l⚔",
            "§e§l⚔ §6§lPvPKits §e§l⚔",
            "§6§l⚔ §c§lPvPKits §6§l⚔"
        )
        
        private val DUEL_TITLE_FRAMES = listOf(
            "§c§l⚔ §4§lDUEL §c§l⚔",
            "§4§l⚔ §c§lDUEL §4§l⚔",
            "§c§l⚔ §4§lDUEL §c§l⚔",
            "§4§l⚔ §c§lDUEL §4§l⚔"
        )
    }
    
    private var titleFrame = 0
    
    /**
     * Create scoreboard for a player
     */
    fun setupScoreboard(player: Player) {
        // Check if player is in duel
        val duelMatch = plugin.duelManager.getPlayerMatch(player.uniqueId)
        
        if (duelMatch != null) {
            setupDuelScoreboard(player, duelMatch.kitName, duelMatch.id.split("_")[1])
        } else {
            setupLobbyScoreboard(player)
        }
    }
    
    /**
     * Setup lobby scoreboard (default)
     */
    private fun setupLobbyScoreboard(player: Player) {
        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        val objective = scoreboard.registerNewObjective("pvpkits", "dummy", getAnimatedTitle())
        objective.displaySlot = DisplaySlot.SIDEBAR
        
        // Create teams for each line
        val lines = listOf(
            "§7§l» §ePlayer Info",
            "§f",
            "§7Name: §f${player.name}",
            "§7Rank: §a#-",
            "§1",
            "§7§l» §cCombat Stats",
            "§2",
            "§7Kills: §a0",
            "§7Deaths: §c0",
            "§7K/D: §e0.00",
            "§7Streak: §60",
            "§3",
            "§7§l» §bLocation",
            "§4",
            "§7Map: §fLobby",
            "§7Queue: §f0",
            "§5",
            "§8play.server.com"
        )
        
        // Add lines (reverse order for correct display)
        lines.reversed().forEachIndexed { index, line ->
            val teamName = "line_${15 - index}"
            val team = scoreboard.registerNewTeam(teamName)
            val entry = getEntryForLine(15 - index)
            team.addEntry(entry)
            team.prefix = line
            objective.getScore(entry).score = 15 - index
        }
        
        player.scoreboard = scoreboard
        playerScoreboards[player.uniqueId] = PlayerScoreboard(scoreboard, objective, false)
    }
    
    /**
     * Setup duel scoreboard
     */
    fun setupDuelScoreboard(player: Player, kitName: String, arenaName: String) {
        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        val objective = scoreboard.registerNewObjective("duel", "dummy", getAnimatedDuelTitle())
        objective.displaySlot = DisplaySlot.SIDEBAR
        
        val duelMatch = plugin.duelManager.getPlayerMatch(player.uniqueId)
        val opponent = duelMatch?.let { Bukkit.getPlayer(it.getOpponent(player.uniqueId))?.name } ?: "???"
        
        // Duel-specific lines
        val lines = listOf(
            "§c§l» §eDUEL INFO",
            "§f",
            "§7Map: §b$arenaName",
            "§1",
            "§7§l» §aYou",
            "§7Name: §f${player.name}",
            "§7Kit: §e$kitName",
            "§7Wins: §a0",
            "§2",
            "§7§l» §cOpponent",
            "§7Name: §c$opponent",
            "§7Wins: §c0",
            "§3",
            "§7Round: §e1/3",
            "§7Score: §f0 - 0",
            "§4",
            "§8play.server.com"
        )
        
        // Add lines (reverse order for correct display)
        lines.reversed().forEachIndexed { index, line ->
            val teamName = "line_${15 - index}"
            val team = scoreboard.registerNewTeam(teamName)
            val entry = getEntryForLine(15 - index)
            team.addEntry(entry)
            team.prefix = line
            objective.getScore(entry).score = 15 - index
        }
        
        player.scoreboard = scoreboard
        playerScoreboards[player.uniqueId] = PlayerScoreboard(scoreboard, objective, true)
    }
    
    /**
     * Update scoreboard for a player
     */
    fun updateScoreboard(player: Player) {
        val ps = playerScoreboards[player.uniqueId] ?: return
        
        // Check if in duel
        val duelMatch = plugin.duelManager.getPlayerMatch(player.uniqueId)
        
        if (duelMatch != null && !ps.isDuelScoreboard) {
            // Switch to duel scoreboard
            setupDuelScoreboard(player, duelMatch.kitName, "Arena")
            return
        } else if (duelMatch == null && ps.isDuelScoreboard) {
            // Switch back to lobby scoreboard
            setupLobbyScoreboard(player)
            return
        }
        
        if (ps.isDuelScoreboard && duelMatch != null) {
            updateDuelScoreboard(player, duelMatch)
        } else {
            updateLobbyScoreboard(player, ps)
        }
    }
    
    /**
     * Update lobby scoreboard
     */
    private fun updateLobbyScoreboard(player: Player, ps: PlayerScoreboard) {
        val stats = plugin.statsManager.getStatsIfExists(player.uniqueId)
        val rank = plugin.statsManager.getPlayerRank(player.uniqueId)
        
        // Update player info
        updateLine(ps, 14, "§7Name: §f${player.name}")
        updateLine(ps, 13, "§7Rank: §a#$rank")
        
        // Update combat stats
        if (stats != null) {
            updateLine(ps, 10, "§7Kills: §a${stats.kills}")
            updateLine(ps, 9, "§7Deaths: §c${stats.deaths}")
            updateLine(ps, 8, "§7K/D: §e${stats.formattedKd}")
            updateLine(ps, 7, "§7Streak: §6${stats.currentKillstreak}")
        }
        
        // Update queue info
        updateLine(ps, 2, "§7Queue: §f${plugin.duelManager.getTotalInQueues()}")
    }
    
    /**
     * Update duel scoreboard
     */
    private fun updateDuelScoreboard(player: Player, duelMatch: com.pvpkits.duel.DuelMatch) {
        val ps = playerScoreboards[player.uniqueId] ?: return
        
        val opponent = Bukkit.getPlayer(duelMatch.getOpponent(player.uniqueId))
        val opponentName = opponent?.name ?: "???"
        val arenaName = duelMatch.spawn1.world?.name?.replace("arena", "Arena ") ?: "Arena"
        
        // Update map
        updateLine(ps, 14, "§7Map: §b$arenaName")
        
        // Update your info
        updateLine(ps, 11, "§7Name: §f${player.name}")
        updateLine(ps, 10, "§7Kit: §e${duelMatch.kitName}")
        updateLine(ps, 9, "§7Wins: §a${duelMatch.getWins(player.uniqueId)}")
        
        // Update opponent info
        updateLine(ps, 6, "§7Name: §c$opponentName")
        updateLine(ps, 5, "§7Wins: §c${duelMatch.getWins(duelMatch.getOpponent(player.uniqueId))}")
        
        // Update round info
        updateLine(ps, 3, "§7Round: §e${duelMatch.currentRound}/${duelMatch.maxRounds}")
        updateLine(ps, 2, "§7Score: §f${duelMatch.player1Wins} - ${duelMatch.player2Wins}")
    }
    
    /**
     * Update arena info on scoreboard
     */
    fun updateArenaInfo(player: Player, arenaName: String, playerCount: Int) {
        val ps = playerScoreboards[player.uniqueId] ?: return
        updateLine(ps, 3, "§7Map: §f$arenaName")
        updateLine(ps, 2, "§7Players: §f$playerCount")
    }
    
    /**
     * Update a specific line
     */
    private fun updateLine(ps: PlayerScoreboard, line: Int, text: String) {
        val team = ps.scoreboard.getTeam("line_$line") ?: return
        team.prefix = text
    }
    
    /**
     * Remove scoreboard for a player
     */
    fun removeScoreboard(player: Player) {
        playerScoreboards.remove(player.uniqueId)
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    }
    
    /**
     * Start auto-update task (Folia-compatible)
     */
    fun startAutoUpdate() {
        SchedulerUtils.runTaskTimer(plugin, UPDATE_INTERVAL, UPDATE_INTERVAL, Runnable {
            // Обновить фрейм анимации
            titleFrame = (titleFrame + 1) % TITLE_FRAMES.size
            
            Bukkit.getOnlinePlayers().forEach { player ->
                if (playerScoreboards.containsKey(player.uniqueId)) {
                    updateScoreboard(player)
                    
                    // Обновить заголовок
                    val ps = playerScoreboards[player.uniqueId]
                    if (ps != null) {
                        ps.objective.displayName(
                            if (ps.isDuelScoreboard) getAnimatedDuelTitle() else getAnimatedTitle()
                        )
                    }
                }
            }
        })
    }
    
    /**
     * Получить анимированный заголовок
     */
    private fun getAnimatedTitle(): Component {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection()
            .deserialize(TITLE_FRAMES[titleFrame])
    }
    
    /**
     * Получить анимированный заголовок дуэли
     */
    private fun getAnimatedDuelTitle(): Component {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacySection()
            .deserialize(DUEL_TITLE_FRAMES[titleFrame % DUEL_TITLE_FRAMES.size])
    }
    
    /**
     * Get entry name for line number
     */
    private fun getEntryForLine(line: Int): String {
        val colors = listOf("§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f")
        return if (line < colors.size) colors[line] + ChatColor.RESET.toString() else "§r$line"
    }
    
    /**
     * Data class to hold scoreboard reference
     */
    data class PlayerScoreboard(
        val scoreboard: Scoreboard,
        val objective: Objective,
        val isDuelScoreboard: Boolean
    )
}
