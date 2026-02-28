package com.pvpkits.rating

import com.github.shynixn.mccoroutine.bukkit.launch
import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class RatingCommand(private val plugin: PvPKitsPlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cOnly players can use this command!")
            return true
        }
        
        when (command.name.lowercase()) {
            "rating", "elo" -> handleRating(sender, args)
            "leaderboard", "lb" -> handleLeaderboard(sender)
        }
        
        return true
    }
    
    private fun handleRating(player: Player, args: Array<out String>) {
        plugin.launch {
            val targetUUID = if (args.isNotEmpty() && player.hasPermission("pvpkits.rating.others")) {
                plugin.server.getPlayer(args[0])?.uniqueId ?: run {
                    player.sendMessage(TextUtils.format("<red>Player not found!"))
                    return@launch
                }
            } else {
                player.uniqueId
            }
            
            val rating = plugin.ratingManager.loadRating(targetUUID)
            val rank = plugin.ratingManager.getPlayerRank(targetUUID)
            val targetName = if (targetUUID == player.uniqueId) "Your" else plugin.server.getPlayer(targetUUID)?.name + "'s"
            
            player.sendMessage("")
            player.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══════════════════════════</bold></gradient>"))
            player.sendMessage(TextUtils.format("<yellow>  $targetName Rating"))
            player.sendMessage("")
            player.sendMessage(TextUtils.format("  <gray>Rating: <white>${rating.rating} ${rating.rank.getColoredName()}"))
            player.sendMessage(TextUtils.format("  <gray>Rank: <yellow>#$rank"))
            player.sendMessage(TextUtils.format("  <gray>Wins: <green>${rating.wins}"))
            player.sendMessage(TextUtils.format("  <gray>Losses: <red>${rating.losses}"))
            player.sendMessage(TextUtils.format("  <gray>Win Rate: <yellow>${String.format("%.1f", rating.getWinRate())}%"))
            player.sendMessage(TextUtils.format("  <gray>Win Streak: <gold>${rating.winStreak}"))
            player.sendMessage(TextUtils.format("  <gray>Best Streak: <gold>${rating.bestWinStreak}"))
            player.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══════════════════════════</bold></gradient>"))
            player.sendMessage("")
        }
    }
    
    private fun handleLeaderboard(player: Player) {
        plugin.launch {
            val topPlayers = plugin.ratingManager.getTopPlayers(10)
            
            player.sendMessage("")
            player.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══ TOP 10 PLAYERS ═══</bold></gradient>"))
            player.sendMessage("")
            
            topPlayers.forEachIndexed { index, rating ->
                val playerName = plugin.server.getOfflinePlayer(rating.uuid).name ?: "Unknown"
                val position = index + 1
                val medal = when (position) {
                    1 -> "🥇"
                    2 -> "🥈"
                    3 -> "🥉"
                    else -> "§7$position."
                }
                
                player.sendMessage(TextUtils.format(
                    "  $medal <yellow>$playerName</yellow> <gray>- <white>${rating.rating}</white> ${rating.rank.getColoredName()}"
                ))
            }
            
            player.sendMessage("")
            player.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══════════════════════════</bold></gradient>"))
            player.sendMessage("")
        }
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) return emptyList()
        
        return when (command.name.lowercase()) {
            "rating", "elo" -> {
                if (args.size == 1 && sender.hasPermission("pvpkits.rating.others")) {
                    plugin.server.onlinePlayers
                        .map { it.name }
                        .filter { it.startsWith(args[0], ignoreCase = true) }
                } else emptyList()
            }
            else -> emptyList()
        }
    }
}
