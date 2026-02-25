package com.pvpkits.duel

import com.pvpkits.PvPKitsPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Command handler for duel system
 */
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
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.GOLD}════════ Duel Commands ════════")
        sender.sendMessage("${ChatColor.YELLOW}/duel queue <kit> ${ChatColor.GRAY}- Join queue for a kit")
        sender.sendMessage("${ChatColor.YELLOW}/duel leave ${ChatColor.GRAY}- Leave queue")
        sender.sendMessage("${ChatColor.YELLOW}/duel info ${ChatColor.GRAY}- View queue info")
        sender.sendMessage("${ChatColor.YELLOW}/duel stats ${ChatColor.GRAY}- View your duel stats")
        sender.sendMessage("${ChatColor.GOLD}═══════════════════════════════")
        sender.sendMessage("")
    }
    
    private fun handleQueue(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players!")
            return
        }
        
        if (args.isEmpty()) {
            // Open kit selection GUI for duels
            openDuelKitMenu(player)
            return
        }
        
        val kitName = args[0]
        plugin.duelManager.joinQueue(player, kitName)
    }
    
    private fun handleLeave(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players!")
            return
        }
        
        if (plugin.duelManager.leaveQueue(player)) {
            player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f)
        } else {
            player.sendMessage("${ChatColor.RED}You are not in a queue!")
        }
    }
    
    private fun handleInfo(sender: CommandSender) {
        sender.sendMessage(plugin.duelManager.getQueueInfo())
    }
    
    private fun handleStats(sender: CommandSender) {
        val player = sender as? Player ?: return
        
        val stats = plugin.statsManager.getStatsIfExists(player.uniqueId)
        if (stats == null) {
            player.sendMessage("${ChatColor.YELLOW}No duel stats yet!")
            return
        }
        
        player.sendMessage("")
        player.sendMessage("${ChatColor.GOLD}════════ Your Duel Stats ════════")
        player.sendMessage("${ChatColor.YELLOW}Kills: ${ChatColor.GREEN}${stats.kills}")
        player.sendMessage("${ChatColor.YELLOW}Deaths: ${ChatColor.RED}${stats.deaths}")
        player.sendMessage("${ChatColor.YELLOW}K/D: ${ChatColor.WHITE}${stats.formattedKd}")
        player.sendMessage("${ChatColor.YELLOW}Best Streak: ${ChatColor.GOLD}${stats.bestKillstreak}")
        player.sendMessage("${ChatColor.GOLD}════════════════════════════════")
        player.sendMessage("")
    }
    
    /**
     * Open kit selection menu for duels
     */
    private fun openDuelKitMenu(player: Player) {
        val kits = plugin.kitManager.getAllKits().toList()
        val rows = ((kits.size + 8) / 9).coerceAtLeast(1).coerceAtMost(6)
        val title = "§6§l⚔ Select Duel Kit"
        val inventory = Bukkit.createInventory(null, rows * 9, title)
        
        kits.forEachIndexed { index, kit ->
            val item = ItemStack(kit.icon?.let { Material.getMaterial(it) } ?: Material.CHEST)
            val meta = item.itemMeta!!
            
            meta.setDisplayName("§e${kit.displayName}")
            
            val lore = mutableListOf<String>()
            lore.add("")
            lore.add("§7Click to join ${kit.displayName} queue")
            lore.add("§7Players waiting: §f${plugin.duelManager.getQueueSize(kit.name)}")
            lore.add("")
            
            meta.lore = lore
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
}
