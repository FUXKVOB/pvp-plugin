package com.pvpkits.duel

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class DuelCommand(private val plugin: PvPKitsPlugin) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (command.name.lowercase()) {
            "duel" -> handleDuel(sender, args)
            "duelqueue", "dq" -> handleQueue(sender, args)
        }
        return true
    }

    private fun handleDuel(sender: CommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            sendDuelHelp(sender)
            return
        }

        when (args[0].lowercase()) {
            "queue", "q" -> handleQueue(sender, args.drop(1).toTypedArray())
            "leave", "l" -> handleLeave(sender)
            "info" -> handleInfo(sender)
            "stats" -> handleStats(sender)
            else -> sendDuelHelp(sender)
        }
    }

    private fun sendDuelHelp(sender: CommandSender) {
        sendBlock(sender, listOf(
            "<gradient:#ffd700:#ffaa00><bold>Duel Commands</bold></gradient>",
            "<yellow>/duel queue <kit></yellow> <gray>- Join queue for a kit",
            "<yellow>/duel leave</yellow> <gray>- Leave queue",
            "<yellow>/duel info</yellow> <gray>- View queue info",
            "<yellow>/duel stats</yellow> <gray>- View your duel stats"
        ))
    }

    private fun handleQueue(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) {
            send(sender, "<red>This command can only be used by players!")
            return
        }

        if (args.isEmpty()) {
            openDuelKitMenu(player)
            return
        }

        plugin.duelManager.joinQueue(player, args[0])
    }

    private fun handleLeave(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            send(sender, "<red>This command can only be used by players!")
            return
        }

        if (plugin.duelManager.leaveQueue(player)) {
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f)
        } else {
            send(player, "<red>You are not in a queue!")
        }
    }

    private fun handleInfo(sender: CommandSender) {
        sender.sendMessage(plugin.duelManager.getQueueInfo())
    }

    private fun handleStats(sender: CommandSender) {
        val player = sender as? Player ?: return
        val stats = plugin.statsManager.getStatsIfExists(player.uniqueId)
        if (stats == null) {
            send(player, "<yellow>No duel stats yet!")
            return
        }

        sendBlock(player, listOf(
            "<gradient:#ffd700:#ffaa00><bold>Your Duel Stats</bold></gradient>",
            "<gray>Kills: <green>${stats.kills}",
            "<gray>Deaths: <red>${stats.deaths}",
            "<gray>K/D: <white>${stats.formattedKd}",
            "<gray>Best Streak: <gold>${stats.bestKillstreak}"
        ))
    }

    private fun openDuelKitMenu(player: Player) {
        val kits = plugin.kitManager.getAllKits().toList()
        val rows = ((kits.size + 8) / 9).coerceAtLeast(1).coerceAtMost(6)
        val inventory = Bukkit.createInventory(null, rows * 9, TextUtils.parseAuto("<gold><bold>Select Duel Kit"))

        kits.forEachIndexed { index, kit ->
            val item = ItemStack(kit.icon?.let { Material.getMaterial(it) } ?: Material.CHEST)
            val meta = item.itemMeta!!
            meta.displayName(TextUtils.parseAuto("<yellow>${kit.displayName}"))
            meta.lore(
                TextUtils.lines(
                    "<gray>Click to join <white>${kit.displayName}</white> <gray>queue",
                    "<gray>Players waiting: <white>${plugin.duelManager.getQueueSize(kit.name)}"
                )
            )
            item.itemMeta = meta
            inventory.setItem(index, item)
        }

        player.openInventory(inventory)
        player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.0f)
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when {
            command.name.lowercase() == "duel" && args.size == 1 -> {
                listOf("queue", "leave", "info", "stats")
                    .filter { it.startsWith(args[0], ignoreCase = true) }
            }
            command.name.lowercase() == "duel" && args.size == 2 && args[0].lowercase() == "queue" -> {
                plugin.kitManager.getAllKits().map { it.name }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
            }
            command.name.lowercase() in listOf("duelqueue", "dq") && args.size == 1 -> {
                plugin.kitManager.getAllKits().map { it.name }
                    .filter { it.startsWith(args[0], ignoreCase = true) }
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
