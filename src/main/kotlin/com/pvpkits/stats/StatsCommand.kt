package com.pvpkits.stats

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
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
                    send(sender, "<red>Player not found: <white>${args[0]}")
                    return
                }
            }
            sender is Player -> sender
            else -> {
                send(sender, "<red>Please specify a player name")
                return
            }
        }

        val stats = plugin.statsManager.getStatsIfExists(target.uniqueId)

        if (stats == null || stats.totalGames == 0) {
            send(sender, "<yellow>${target.name} has no stats yet!")
            return
        }

        val rank = plugin.statsManager.getPlayerRank(target.uniqueId)

        sendBlock(
            sender,
            listOf(
                "<gold>===============================",
                "<yellow><bold>Stats for <white>${target.name}",
                "<gray>Rank: <green>#$rank",
                "<red>Kills: <white>${stats.kills}",
                "<dark_red>Deaths: <white>${stats.deaths}",
                "<yellow>K/D Ratio: <white>${stats.formattedKd}",
                "<light_purple>Current Streak: <white>${stats.currentKillstreak}",
                "<gold>Best Streak: <white>${stats.bestKillstreak}",
                "<aqua>Favorite Kit: <white>${stats.favoriteKit ?: "None"}",
                "<gray>Total Games: <white>${stats.totalGames}",
                "<gold>==============================="
            )
        )
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
        sendBlock(
            sender,
            buildList {
                add("<gold>========= <yellow>$title <gold>=========")
                leaderboard.forEachIndexed { index, stats ->
                    val medal = when (index) {
                        0 -> "<gold>🥇"
                        1 -> "<gray>🥈"
                        2 -> "<dark_gray>🥉"
                        else -> "<white>${index + 1}."
                    }

                    add("$medal <white>${stats.playerName} <gray>- <red>${stats.kills} kills <dark_gray>| <yellow>KD: <white>${stats.formattedKd}")
                }
                add("<gray>Use <yellow>/top kd</yellow> <gray>or <yellow>/top streak")
                add("<gold>===============================")
            }
        )
    }

    private fun openLeaderboardGUI(player: Player, leaderboard: List<PlayerStats>, title: String, type: String) {
        val inventory = Bukkit.createInventory(null, 36, TextUtils.parseAuto("<gold><bold>$title"))

        for (i in 0 until 36) {
            inventory.setItem(i, ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
                itemMeta = itemMeta!!.apply {
                    displayName(Component.space())
                }
            })
        }

        leaderboard.forEachIndexed { index, stats ->
            val slot = when (index) {
                0 -> 13
                1 -> 21
                2 -> 23
                else -> 28 + (index - 3)
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
                    0 -> "<gold><bold>🥇 #1"
                    1 -> "<gray><bold>🥈 #2"
                    2 -> "<red><bold>🥉 #3"
                    else -> "<white>#${index + 1}"
                }

                meta.displayName(TextUtils.parseAuto("$medal <white>${stats.playerName}"))
                meta.lore(
                    TextUtils.lines(
                        "<gray>Mode: <white>$type",
                        "<red>Kills: <white>${stats.kills}",
                        "<dark_red>Deaths: <white>${stats.deaths}",
                        "<yellow>K/D: <white>${stats.formattedKd}",
                        "<gold>Best Streak: <white>${stats.bestKillstreak}"
                    )
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

    private fun send(sender: CommandSender, text: String) {
        sender.sendMessage(TextUtils.parseAuto(text))
    }

    private fun sendBlock(sender: CommandSender, lines: List<String>) {
        sender.sendMessage(Component.empty())
        lines.forEach { send(sender, it) }
        sender.sendMessage(Component.empty())
    }
}
