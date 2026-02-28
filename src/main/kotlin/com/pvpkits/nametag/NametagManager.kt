package com.pvpkits.nametag

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.SchedulerUtils
import com.pvpkits.utils.TextUtils
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team

class NametagManager(private val plugin: PvPKitsPlugin) {
    
    private val scoreboards = mutableMapOf<Player, Scoreboard>()
    
    fun enable() {
        // Start update task (Folia-compatible)
        SchedulerUtils.runTaskTimer(plugin, 0L, 10L, Runnable {
            updateAllNametags()
        })
    }
    
    fun setupPlayer(player: Player) {
        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        scoreboards[player] = scoreboard
        player.scoreboard = scoreboard
        
        // Create teams for all online players
        Bukkit.getOnlinePlayers().forEach { target ->
            createTeamForPlayer(scoreboard, target)
        }
    }
    
    fun removePlayer(player: Player) {
        scoreboards.remove(player)
    }
    
    private fun updateAllNametags() {
        Bukkit.getOnlinePlayers().forEach { player ->
            updatePlayerNametag(player)
        }
    }
    
    private fun updatePlayerNametag(player: Player) {
        val health = player.health.toInt()
        val maxHealth = player.maxHealth.toInt()
        val ping = getPing(player)
        
        // Update for all other players
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (viewer != player) {
                val scoreboard = scoreboards[viewer] ?: return@forEach
                val team = scoreboard.getTeam(player.name) ?: createTeamForPlayer(scoreboard, player)
                
                updateTeamNametag(team, player, health, maxHealth, ping)
            }
        }
    }
    
    private fun createTeamForPlayer(scoreboard: Scoreboard, player: Player): Team {
        var team = scoreboard.getTeam(player.name)
        if (team == null) {
            team = scoreboard.registerNewTeam(player.name)
            team.addEntry(player.name)
        }
        return team
    }
    
    private fun updateTeamNametag(team: Team, player: Player, health: Int, maxHealth: Int, ping: Int) {
        val config = plugin.config
        
        if (!config.getBoolean("nametag.enabled", true)) {
            team.prefix(Component.empty())
            team.suffix(Component.empty())
            return
        }
        
        val format = config.getString("nametag.format") ?: "{name}\n{health} {ping}"
        
        // Build nametag
        val nametagText = format
            .replace("{name}", player.name)
            .replace("{health}", formatHealth(health, maxHealth))
            .replace("{ping}", formatPing(ping))
            .replace("{max_health}", maxHealth.toString())
        
        // Split by newline for prefix/suffix
        val lines = nametagText.split("\\n")
        
        when {
            lines.size >= 2 -> {
                // First line as prefix, second as suffix
                team.prefix(TextUtils.parseAuto(lines[0]))
                team.suffix(TextUtils.parseAuto(lines[1]))
            }
            lines.size == 1 -> {
                // Single line as prefix
                team.prefix(TextUtils.parseAuto(lines[0]))
                team.suffix(Component.empty())
            }
        }
    }
    
    private fun formatHealth(health: Int, maxHealth: Int): String {
        val percentage = (health.toDouble() / maxHealth.toDouble()) * 100
        
        // Градиентные цвета для здоровья (2026 визуал)
        val color = when {
            percentage >= 90 -> "<gradient:#00ff00:#55ff55>"
            percentage >= 75 -> "<gradient:#55ff55:#ffff00>"
            percentage >= 50 -> "<gradient:#ffff00:#ffaa00>"
            percentage >= 25 -> "<gradient:#ffaa00:#ff5500>"
            else -> "<gradient:#ff5500:#ff0000>"
        }
        
        // Красивые сердечки с анимацией
        val heartCount = (health / 2.0).toInt().coerceAtLeast(1).coerceAtMost(10)
        val hearts = "❤".repeat(heartCount)
        
        // Добавить половинку если нечетное
        val halfHeart = if (health % 2 == 1) "💔" else ""
        
        return "$color$hearts$halfHeart</gradient> <gray>$health<dark_gray>/$maxHealth"
    }
    
    private fun formatPing(ping: Int): String {
        // Градиентные цвета для пинга (2026 визуал)
        val color = when {
            ping < 30 -> "<gradient:#00ff00:#55ff55>"
            ping < 50 -> "<gradient:#55ff55:#ffff00>"
            ping < 100 -> "<gradient:#ffff00:#ffaa00>"
            ping < 150 -> "<gradient:#ffaa00:#ff5500>"
            else -> "<gradient:#ff5500:#ff0000>"
        }
        
        // Красивые индикаторы пинга
        val bars = when {
            ping < 30 -> "█████"
            ping < 50 -> "████▓"
            ping < 100 -> "███▓▓"
            ping < 150 -> "██▓▓▓"
            ping < 200 -> "█▓▓▓▓"
            else -> "▓▓▓▓▓"
        }
        
        // Эмодзи индикатор
        val emoji = when {
            ping < 50 -> "🟢"
            ping < 100 -> "🟡"
            ping < 200 -> "🟠"
            else -> "🔴"
        }
        
        return "$color$bars</gradient> $emoji <gray>${ping}ms"
    }
    
    private fun getPing(player: Player): Int {
        return runCatching { player.ping }.getOrDefault(0)
    }
    
    fun onPlayerJoin(player: Player) {
        setupPlayer(player)
        
        // Add new player to all existing scoreboards
        Bukkit.getOnlinePlayers().forEach { other ->
            if (other != player) {
                val scoreboard = scoreboards[other]
                if (scoreboard != null) {
                    createTeamForPlayer(scoreboard, player)
                }
            }
        }
    }
    
    fun onPlayerQuit(player: Player) {
        removePlayer(player)
        
        // Remove player from all scoreboards
        scoreboards.values.forEach { scoreboard ->
            scoreboard.getTeam(player.name)?.unregister()
        }
    }
}
