package com.pvpkits.stats

import com.pvpkits.PvPKitsPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * Command handler for stats and leaderboard
 */
class StatsCommand(private val plugin: PvPKitsPlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (command.name.lowercase()) {
            "stats" -> handleStats(sender, args)
            "top", "leaderboard" -> handleLeaderboard(sender, args)
        }
        return true
    }
    
    private fun handleStats(sender: CommandSender, args: Array<out String>) {
        val target = when {
            args.isNotEmpty() && sender.hasPermission("pvpkits.stats.others") -> {
                Bukkit.getPlayer(args[0]) ?: run {
                    sender.sendMessage("${ChatColor.RED}Player not found: ${args[0]}")
                    return
                }
            }
            sender is Player -> sender
            else -> {
                sender.sendMessage("${ChatColor.RED}Please specify a player name")
                return
            }
        }
        
        val stats = plugin.statsManager.getStatsIfExists(target.uniqueId)
        
        if (stats == null || stats.totalGames == 0) {
            sender.sendMessage("${ChatColor.YELLOW}${target.name} has no stats yet!")
            return
        }
        
        val rank = plugin.statsManager.getPlayerRank(target.uniqueId)
        
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.GOLD}═══════════════════════════════")
        sender.sendMessage("${ChatColor.YELLOW}📊 Stats for ${ChatColor.WHITE}${target.name}")
        sender.sendMessage("${ChatColor.GOLD}═══════════════════════════════")
        sender.sendMessage("${ChatColor.GRAY}Rank: ${ChatColor.GREEN}#$rank")
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.RED}⚔ Kills: ${ChatColor.WHITE}${stats.kills}")
        sender.sendMessage("${ChatColor.DARK_RED}💀 Deaths: ${ChatColor.WHITE}${stats.deaths}")
        sender.sendMessage("${ChatColor.YELLOW}📈 K/D Ratio: ${ChatColor.WHITE}${stats.formattedKd}")
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.LIGHT_PURPLE}🔥 Current Streak: ${ChatColor.WHITE}${stats.currentKillstreak}")
        sender.sendMessage("${ChatColor.GOLD}⭐ Best Streak: ${ChatColor.WHITE}${stats.bestKillstreak}")
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.AQUA}📦 Favorite Kit: ${ChatColor.WHITE}${stats.favoriteKit ?: "None"}")
        sender.sendMessage("${ChatColor.GRAY}Total Games: ${ChatColor.WHITE}${stats.totalGames}")
        sender.sendMessage("${ChatColor.GOLD}═══════════════════════════════")
        sender.sendMessage("")
    }
    
    private fun handleLeaderboard(sender: CommandSender, args: Array<out String>) {
        val type = args.firstOrNull()?.lowercase() ?: "kills"
        val limit = 10
        
        val leaderboard = when (type) {
            "kd", "ratio" -> plugin.statsManager.getLeaderboardByKd(limit)
            "streak", "killstreak" -> plugin.statsManager.getLeaderboardByKillstreak(limit)
            else -> plugin.statsManager.getLeaderboard(limit)
        }
        
        val title = when (type) {
            "kd", "ratio" -> "K/D Ratio Leaderboard"
            "streak", "killstreak" -> "Best Killstreaks"
            else -> "Top Killers"
        }
        
        if (sender is Player && plugin.config.getBoolean("stats.gui-leaderboard", true)) {
            openLeaderboardGUI(sender, leaderboard, title, type)
        } else {
            sendLeaderboardChat(sender, leaderboard, title)
        }
    }
    
    private fun sendLeaderboardChat(sender: CommandSender, leaderboard: List<PlayerStats>, title: String) {
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.GOLD}═════════ ${ChatColor.YELLOW}$title ${ChatColor.GOLD}═════════")
        sender.sendMessage("")
        
        leaderboard.forEachIndexed { index, stats ->
            val medal = when (index) {
                0 -> "${ChatColor.GOLD}🥇"
                1 -> "${ChatColor.GRAY}🥈"
                2 -> "${ChatColor.DARK_GRAY}🥉"
                else -> "${ChatColor.WHITE}${index + 1}."
            }
            
            sender.sendMessage("$medal ${ChatColor.WHITE}${stats.playerName} " +
                "${ChatColor.GRAY}- ${ChatColor.RED}${stats.kills} kills " +
                "${ChatColor.DARK_GRAY}| ${ChatColor.YELLOW}KD: ${stats.formattedKd}")
        }
        
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.GRAY}Use ${ChatColor.YELLOW}/top kd ${ChatColor.GRAY}or ${ChatColor.YELLOW}/top streak")
        sender.sendMessage("${ChatColor.GOLD}═══════════════════════════════")
        sender.sendMessage("")
    }
    
    private fun openLeaderboardGUI(player: Player, leaderboard: List<PlayerStats>, title: String, type: String) {
        val inventory: Inventory = Bukkit.createInventory(null, 36, "§6§l$title")
        
        // Fill with glass panes
        for (i in 0 until 36) {
            inventory.setItem(i, ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
                val meta = itemMeta!!
                meta.setDisplayName(" ")
                itemMeta = meta
            })
        }
        
        // Add leaderboard entries
        leaderboard.forEachIndexed { index, stats ->
            val slot = when (index) {
                0 -> 13 // Center top
                1 -> 21 // Left of center
                2 -> 23 // Right of center
                else -> 28 + (index - 3) // Bottom row
            }
            
            if (slot < 36) {
                val material = when (index) {
                    0 -> Material.GOLD_BLOCK
                    1 -> Material.IRON_BLOCK
                    2 -> Material.COPPER_BLOCK
                    else -> Material.PLAYER_HEAD
                }
                
                val item = ItemStack(material)
                val meta = item.itemMeta!!
                
                val medal = when (index) {
                    0 -> "§6§l🥇 #1"
                    1 -> "§7§l🥈 #2"
                    2 -> "§c§l🥉 #3"
                    else -> "§f#${index + 1}"
                }
                
                meta.setDisplayName("$medal §f${stats.playerName}")
                meta.lore = listOf(
                    "",
                    "§c⚔ Kills: §f${stats.kills}",
                    "§4💀 Deaths: §f${stats.deaths}",
                    "§e📈 K/D: §f${stats.formattedKd}",
                    "§6⭐ Best Streak: §f${stats.bestKillstreak}",
                    ""
                )
                item.itemMeta = meta
                inventory.setItem(slot, item)
            }
        }
        
        player.openInventory(inventory)
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when {
            command.name.lowercase() == "stats" && args.size == 1 -> {
                Bukkit.getOnlinePlayers().map { it.name }.filter { 
                    it.startsWith(args[0], ignoreCase = true) 
                }
            }
            command.name.lowercase() in listOf("top", "leaderboard") && args.size == 1 -> {
                listOf("kills", "kd", "streak").filter { 
                    it.startsWith(args[0], ignoreCase = true) 
                }
            }
            else -> emptyList()
        }
    }
}
