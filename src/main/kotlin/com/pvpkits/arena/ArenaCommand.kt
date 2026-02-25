package com.pvpkits.arena

import com.pvpkits.PvPKitsPlugin
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Arena command handler
 */
class ArenaCommand(private val plugin: PvPKitsPlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        when (command.name.lowercase()) {
            "arena" -> handleArenaAdmin(sender, args)
            "join" -> handleJoin(sender, args)
            "leave" -> handleLeave(sender)
            "queue" -> handleQueue(sender)
            "arenas" -> handleArenasList(sender)
        }
        return true
    }
    
    private fun handleArenaAdmin(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pvpkits.admin")) {
            sender.sendMessage("${ChatColor.RED}No permission!")
            return
        }
        
        if (args.isEmpty()) {
            sendArenaHelp(sender)
            return
        }
        
        when (args[0].lowercase()) {
            "create" -> createArena(sender, args)
            "delete" -> deleteArena(sender, args)
            "list" -> listArenas(sender)
            "info" -> arenaInfo(sender, args)
            "setspawn" -> setSpawn(sender, args)
            "setlobby" -> setLobby(sender, args)
            "enable", "disable" -> toggleArena(sender, args)
            else -> sendArenaHelp(sender)
        }
    }
    
    private fun sendArenaHelp(sender: CommandSender) {
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.GOLD}════════ Arena Commands ════════")
        sender.sendMessage("${ChatColor.YELLOW}/arena create <name> ${ChatColor.GRAY}- Create new arena")
        sender.sendMessage("${ChatColor.YELLOW}/arena delete <name> ${ChatColor.GRAY}- Delete arena")
        sender.sendMessage("${ChatColor.YELLOW}/arena list ${ChatColor.GRAY}- List all arenas")
        sender.sendMessage("${ChatColor.YELLOW}/arena info <name> ${ChatColor.GRAY}- Arena info")
        sender.sendMessage("${ChatColor.YELLOW}/arena setspawn <name> ${ChatColor.GRAY}- Add spawn point")
        sender.sendMessage("${ChatColor.YELLOW}/arena setlobby <name> ${ChatColor.GRAY}- Set lobby spawn")
        sender.sendMessage("${ChatColor.YELLOW}/arena enable/disable <name> ${ChatColor.GRAY}- Toggle arena")
        sender.sendMessage("${ChatColor.GOLD}═══════════════════════════════")
        sender.sendMessage("")
    }
    
    private fun createArena(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /arena create <name>")
            return
        }
        
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players!")
            return
        }
        
        val name = args[1]
        
        if (plugin.arenaManager.getArena(name) != null) {
            sender.sendMessage("${ChatColor.RED}Arena '$name' already exists!")
            return
        }
        
        // Create arena with current location as first spawn
        val loc = player.location
        val arena = plugin.arenaManager.createArena(
            name = name,
            displayName = name,
            worldName = loc.world!!.name,
            spawns = listOf(loc)
        )
        
        if (arena != null) {
            sender.sendMessage("${ChatColor.GREEN}Arena '$name' created!")
            sender.sendMessage("${ChatColor.GRAY}Add more spawns with ${ChatColor.YELLOW}/arena setspawn $name")
        } else {
            sender.sendMessage("${ChatColor.RED}Failed to create arena!")
        }
    }
    
    private fun deleteArena(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /arena delete <name>")
            return
        }
        
        val name = args[1]
        
        if (plugin.arenaManager.deleteArena(name)) {
            sender.sendMessage("${ChatColor.GREEN}Arena '$name' deleted!")
        } else {
            sender.sendMessage("${ChatColor.RED}Arena '$name' not found!")
        }
    }
    
    private fun listArenas(sender: CommandSender) {
        val arenas = plugin.arenaManager.getAllArenas()
        
        if (arenas.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}No arenas created yet!")
            return
        }
        
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.GOLD}════════ Arenas ════════")
        arenas.forEach { arena ->
            val status = if (arena.enabled) "${ChatColor.GREEN}✓" else "${ChatColor.RED}✗"
            val players = plugin.arenaManager.getPlayerCount(arena.name)
            sender.sendMessage("$status ${ChatColor.YELLOW}${arena.displayName} ${ChatColor.GRAY}($players/${arena.maxPlayers})")
        }
        sender.sendMessage("${ChatColor.GOLD}════════════════════════")
        sender.sendMessage("")
    }
    
    private fun arenaInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /arena info <name>")
            return
        }
        
        val arena = plugin.arenaManager.getArena(args[1])
        if (arena == null) {
            sender.sendMessage("${ChatColor.RED}Arena not found!")
            return
        }
        
        val players = plugin.arenaManager.getPlayerCount(arena.name)
        
        sender.sendMessage("")
        sender.sendMessage("${ChatColor.GOLD}════════ ${arena.displayName} ════════")
        sender.sendMessage("${ChatColor.YELLOW}Status: ${if (arena.enabled) "${ChatColor.GREEN}Enabled" else "${ChatColor.RED}Disabled"}")
        sender.sendMessage("${ChatColor.YELLOW}World: ${ChatColor.WHITE}${arena.worldName}")
        sender.sendMessage("${ChatColor.YELLOW}Spawns: ${ChatColor.WHITE}${arena.spawns.size}")
        sender.sendMessage("${ChatColor.YELLOW}Players: ${ChatColor.WHITE}$players/${arena.maxPlayers}")
        sender.sendMessage("${ChatColor.YELLOW}Min Players: ${ChatColor.WHITE}${arena.minPlayers}")
        sender.sendMessage("${ChatColor.GOLD}═══════════════════════════════")
        sender.sendMessage("")
    }
    
    private fun setSpawn(sender: CommandSender, args: Array<out String>) {
        // Implementation for adding spawn points
        sender.sendMessage("${ChatColor.YELLOW}Spawn point configuration - use /arena create for new spawns")
    }
    
    private fun setLobby(sender: CommandSender, args: Array<out String>) {
        // Implementation for setting lobby spawn
        sender.sendMessage("${ChatColor.YELLOW}Lobby configuration - set in config.yml")
    }
    
    private fun toggleArena(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage("${ChatColor.RED}Usage: /arena ${args[0]} <name>")
            return
        }
        
        sender.sendMessage("${ChatColor.YELLOW}Arena toggle - modify arenas.yml directly or recreate arena")
    }
    
    private fun handleJoin(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players!")
            return
        }
        
        val arenaName = if (args.isNotEmpty()) args[0] else {
            // Find arena with most players or first available
            plugin.arenaManager.getEnabledArenas().firstOrNull()?.name ?: run {
                sender.sendMessage("${ChatColor.RED}No arenas available!")
                return
            }
        }
        
        if (plugin.arenaManager.joinArena(player, arenaName)) {
            val arena = plugin.arenaManager.getArena(arenaName)
            player.sendMessage("${ChatColor.GREEN}Joined ${ChatColor.YELLOW}${arena?.displayName ?: arenaName}!")
        } else {
            player.sendMessage("${ChatColor.RED}Failed to join arena! It may be full or disabled.")
        }
    }
    
    private fun handleLeave(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players!")
            return
        }
        
        if (plugin.arenaManager.leaveArena(player)) {
            player.sendMessage("${ChatColor.YELLOW}Left the arena!")
        } else {
            player.sendMessage("${ChatColor.RED}You are not in an arena!")
        }
    }
    
    private fun handleQueue(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players!")
            return
        }
        
        if (plugin.lobbyManager.isInQueue(player)) {
            plugin.lobbyManager.removeFromQueue(player)
        } else {
            plugin.lobbyManager.addToQueue(player)
        }
    }
    
    private fun handleArenasList(sender: CommandSender) {
        listArenas(sender)
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when {
            command.name.lowercase() == "arena" && args.size == 1 -> {
                listOf("create", "delete", "list", "info", "setspawn", "setlobby", "enable", "disable")
                    .filter { it.startsWith(args[0], ignoreCase = true) }
            }
            command.name.lowercase() == "arena" && args.size == 2 && args[0].lowercase() in listOf("delete", "info", "enable", "disable", "setspawn") -> {
                plugin.arenaManager.getAllArenas().map { it.name }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
            }
            command.name.lowercase() == "join" && args.size == 1 -> {
                plugin.arenaManager.getEnabledArenas().map { it.name }
                    .filter { it.startsWith(args[0], ignoreCase = true) }
            }
            else -> emptyList()
        }
    }
}
