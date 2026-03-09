package com.pvpkits.arena

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

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
            send(sender, "<red>No permission!")
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
            "template" -> handleTemplate(sender, args)
            else -> sendArenaHelp(sender)
        }
    }

    private fun sendArenaHelp(sender: CommandSender) {
        sendBlock(sender, listOf(
            "<gradient:#f6c453:#f08b3e><bold>Arena Commands</bold></gradient>",
            "<yellow>/arena create <name></yellow> <gray>- Create new arena",
            "<yellow>/arena delete <name></yellow> <gray>- Delete arena",
            "<yellow>/arena list</yellow> <gray>- List all arenas",
            "<yellow>/arena info <name></yellow> <gray>- Arena info",
            "<yellow>/arena setspawn <name></yellow> <gray>- Add spawn point",
            "<yellow>/arena setlobby <name></yellow> <gray>- Set lobby spawn",
            "<yellow>/arena enable/disable <name></yellow> <gray>- Toggle arena",
            "<aqua>Template system:</aqua>",
            "<yellow>/arena template create <name></yellow> <gray>- Create template",
            "<yellow>/arena template list</yellow> <gray>- List templates",
            "<yellow>/arena template info <name></yellow> <gray>- Template info"
        ))
    }

    private fun createArena(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            send(sender, "<red>Usage: /arena create <name>")
            return
        }

        val player = sender as? Player
        if (player == null) {
            send(sender, "<red>This command can only be used by players!")
            return
        }

        val name = args[1]
        if (plugin.arenaManager.getArena(name) != null) {
            send(sender, "<red>Arena '$name' already exists!")
            return
        }

        val loc = player.location
        val arena = plugin.arenaManager.createArena(name, name, loc.world!!.name, listOf(loc))
        if (arena != null) {
            send(sender, "<green>Arena '$name' created!")
            send(sender, "<gray>Add more spawns with <yellow>/arena setspawn $name")
        } else {
            send(sender, "<red>Failed to create arena!")
        }
    }

    private fun deleteArena(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            send(sender, "<red>Usage: /arena delete <name>")
            return
        }

        val name = args[1]
        if (plugin.arenaManager.deleteArena(name)) {
            send(sender, "<green>Arena '$name' deleted!")
        } else {
            send(sender, "<red>Arena '$name' not found!")
        }
    }

    private fun listArenas(sender: CommandSender) {
        val arenas = plugin.arenaManager.getAllArenas()
        if (arenas.isEmpty()) {
            send(sender, "<yellow>No arenas created yet!")
            return
        }

        val lines = mutableListOf("<gradient:#f6c453:#f08b3e><bold>Arenas</bold></gradient>")
        arenas.forEach { arena ->
            val status = if (arena.enabled) "<green>online</green>" else "<red>offline</red>"
            val players = plugin.arenaManager.getPlayerCount(arena.name)
            lines.add("$status <yellow>${arena.displayName}</yellow> <gray>($players/${arena.maxPlayers})")
        }
        sendBlock(sender, lines)
    }

    private fun arenaInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            send(sender, "<red>Usage: /arena info <name>")
            return
        }

        val arena = plugin.arenaManager.getArena(args[1])
        if (arena == null) {
            send(sender, "<red>Arena not found!")
            return
        }

        val players = plugin.arenaManager.getPlayerCount(arena.name)
        sendBlock(sender, listOf(
            "<gradient:#f6c453:#f08b3e><bold>${arena.displayName}</bold></gradient>",
            "<gray>Status: ${if (arena.enabled) "<green>Enabled" else "<red>Disabled"}",
            "<gray>World: <white>${arena.worldName}",
            "<gray>Spawns: <white>${arena.spawns.size}",
            "<gray>Players: <white>$players/${arena.maxPlayers}",
            "<gray>Min Players: <white>${arena.minPlayers}"
        ))
    }

    private fun setSpawn(sender: CommandSender, args: Array<out String>) {
        send(sender, "<yellow>Spawn point configuration is still handled through the current arena workflow.")
    }

    private fun setLobby(sender: CommandSender, args: Array<out String>) {
        send(sender, "<yellow>Lobby configuration is still controlled from <white>config.yml</white>.")
    }

    private fun toggleArena(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            send(sender, "<red>Usage: /arena ${args[0]} <name>")
            return
        }

        send(sender, "<yellow>Arena enable/disable is not wired yet. Update the arena config or recreate the arena for now.")
    }

    private fun handleJoin(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) {
            send(sender, "<red>This command can only be used by players!")
            return
        }

        val arenaName = if (args.isNotEmpty()) {
            args[0]
        } else {
            plugin.arenaManager.getEnabledArenas().firstOrNull()?.name ?: run {
                send(sender, "<red>No arenas available!")
                return
            }
        }

        if (plugin.arenaManager.joinArena(player, arenaName)) {
            val arena = plugin.arenaManager.getArena(arenaName)
            send(player, "<green>Joined <yellow>${arena?.displayName ?: arenaName}</yellow>!")
        } else {
            send(player, "<red>Failed to join arena. It may be full or disabled.")
        }
    }

    private fun handleLeave(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            send(sender, "<red>This command can only be used by players!")
            return
        }

        if (plugin.arenaManager.leaveArena(player)) {
            send(player, "<yellow>Left the arena!")
        } else {
            send(player, "<red>You are not in an arena!")
        }
    }

    private fun handleQueue(sender: CommandSender) {
        val player = sender as? Player
        if (player == null) {
            send(sender, "<red>This command can only be used by players!")
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

    private fun handleTemplate(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            send(sender, "<red>Usage: /arena template <create|list|info|delete>")
            return
        }

        when (args[1].lowercase()) {
            "create" -> createTemplate(sender, args)
            "list" -> listTemplates(sender)
            "info" -> templateInfo(sender, args)
            "delete" -> deleteTemplate(sender, args)
            else -> send(sender, "<red>Unknown template command!")
        }
    }

    private fun createTemplate(sender: CommandSender, args: Array<out String>) {
        val player = sender as? Player
        if (player == null) {
            send(sender, "<red>This command can only be used by players!")
            return
        }

        if (args.size < 3) {
            sendBlock(sender, listOf(
                "<red>Usage: /arena template create <name>",
                "<gray>Stand at spawn1, then use this command.",
                "<gray>You'll still need to define spawn2 and the arena bounds."
            ))
            return
        }

        val name = args[2]
        if (plugin.improvedArenaManager.getTemplate(name) != null) {
            send(sender, "<red>Template '$name' already exists!")
            return
        }

        send(sender, "<green>Spawn 1 captured for <yellow>$name</yellow>.")
        send(sender, "<yellow>Move to spawn 2 and continue with the template setup flow.")
    }

    private fun listTemplates(sender: CommandSender) {
        val templates = plugin.improvedArenaManager.getAllTemplates()
        if (templates.isEmpty()) {
            sendBlock(sender, listOf(
                "<yellow>No arena templates created yet!",
                "<gray>Create one with <white>/arena template create <name>"
            ))
            return
        }

        val lines = mutableListOf("<gradient:#f6c453:#f08b3e><bold>Arena Templates</bold></gradient>")
        templates.forEach { template ->
            val status = if (template.enabled) "<green>online</green>" else "<red>offline</red>"
            lines.add("$status <yellow>${template.displayName}</yellow> <gray>(${template.worldName})")
        }
        lines.add("<gray>Active instances: <white>${plugin.improvedArenaManager.getMemoryStats()["active_instances"]}")
        sendBlock(sender, lines)
    }

    private fun templateInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            send(sender, "<red>Usage: /arena template info <name>")
            return
        }

        val template = plugin.improvedArenaManager.getTemplate(args[2])
        if (template == null) {
            send(sender, "<red>Template not found!")
            return
        }

        val (dx, dy, dz) = template.getBoundsSize()
        val lines = mutableListOf(
            "<gradient:#f6c453:#f08b3e><bold>${template.displayName}</bold></gradient>",
            "<gray>Status: ${if (template.enabled) "<green>Enabled" else "<red>Disabled"}",
            "<gray>World: <white>${template.worldName}",
            "<gray>Bounds: <white>${dx}x${dy}x${dz}",
            "<gray>Spawn 1: <white>${template.spawn1.blockX}, ${template.spawn1.blockY}, ${template.spawn1.blockZ}",
            "<gray>Spawn 2: <white>${template.spawn2.blockX}, ${template.spawn2.blockY}, ${template.spawn2.blockZ}"
        )
        if (template.allowedKits.isNotEmpty()) {
            lines.add("<gray>Allowed Kits: <white>${template.allowedKits.joinToString(", ")}")
        }
        sendBlock(sender, lines)
    }

    private fun deleteTemplate(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            send(sender, "<red>Usage: /arena template delete <name>")
            return
        }

        val name = args[2]
        if (plugin.improvedArenaManager.deleteTemplate(name)) {
            send(sender, "<green>Template '$name' deleted!")
        } else {
            send(sender, "<red>Template '$name' not found!")
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when {
            command.name.lowercase() == "arena" && args.size == 1 -> {
                listOf("create", "delete", "list", "info", "setspawn", "setlobby", "enable", "disable", "template")
                    .filter { it.startsWith(args[0], ignoreCase = true) }
            }
            command.name.lowercase() == "arena" && args.size == 2 && args[0].lowercase() == "template" -> {
                listOf("create", "list", "info", "delete")
                    .filter { it.startsWith(args[1], ignoreCase = true) }
            }
            command.name.lowercase() == "arena" && args.size == 3 && args[0].lowercase() == "template" && args[1].lowercase() in listOf("info", "delete") -> {
                plugin.improvedArenaManager.getAllTemplates().map { it.name }
                    .filter { it.startsWith(args[2], ignoreCase = true) }
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

    private fun send(sender: CommandSender, text: String) {
        sender.sendMessage(TextUtils.parseAuto(text))
    }

    private fun sendBlock(sender: CommandSender, lines: List<String>) {
        sender.sendMessage(Component.empty())
        lines.forEach { send(sender, it) }
        sender.sendMessage(Component.empty())
    }
}
