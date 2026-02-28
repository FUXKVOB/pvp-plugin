package com.pvpkits.tournament

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class TournamentCommand(private val plugin: PvPKitsPlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "join" -> handleJoin(sender, args)
            "leave" -> handleLeave(sender)
            "start" -> handleStart(sender, args)
            "list" -> handleList(sender)
            "info" -> handleInfo(sender, args)
            else -> sendHelp(sender)
        }
        
        return true
    }
    
    private fun handleCreate(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pvpkits.tournament.create")) {
            sender.sendMessage(TextUtils.format("<red>No permission!"))
            return
        }
        
        if (args.size < 4) {
            sender.sendMessage(TextUtils.format("<red>Usage: /tournament create <name> <kit> <maxPlayers> [single|double]"))
            return
        }
        
        val name = args[1]
        val kitName = args[2]
        val maxPlayers = args[3].toIntOrNull() ?: run {
            sender.sendMessage(TextUtils.format("<red>Invalid max players number!"))
            return
        }
        
        if (maxPlayers < 2 || maxPlayers > 64) {
            sender.sendMessage(TextUtils.format("<red>Max players must be between 2 and 64!"))
            return
        }
        
        // Check if kit exists
        if (plugin.kitManager.getKit(kitName) == null) {
            sender.sendMessage(TextUtils.format("<red>Kit not found: $kitName"))
            return
        }
        
        val bracketType = if (args.size > 4) {
            when (args[4].lowercase()) {
                "double" -> BracketType.DOUBLE_ELIMINATION
                else -> BracketType.SINGLE_ELIMINATION
            }
        } else {
            BracketType.SINGLE_ELIMINATION
        }
        
        val tournament = plugin.tournamentManager.createTournament(name, kitName, maxPlayers, bracketType)
        
        sender.sendMessage(TextUtils.format("<green>✓ Tournament created!"))
        sender.sendMessage(TextUtils.format("<gray>ID: <yellow>${tournament.id}"))
        sender.sendMessage(TextUtils.format("<gray>Players can join with: <yellow>/tournament join ${tournament.id}"))
    }
    
    private fun handleJoin(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            sender.sendMessage("§cOnly players can join tournaments!")
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(TextUtils.format("<red>Usage: /tournament join <id>"))
            return
        }
        
        val tournamentId = args[1]
        plugin.tournamentManager.joinTournament(sender, tournamentId)
    }
    
    private fun handleLeave(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage("§cOnly players can leave tournaments!")
            return
        }
        
        if (plugin.tournamentManager.leaveTournament(sender)) {
            sender.sendMessage(TextUtils.format("<yellow>Left tournament"))
        } else {
            sender.sendMessage(TextUtils.format("<red>You are not in a tournament!"))
        }
    }
    
    private fun handleStart(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("pvpkits.tournament.start")) {
            sender.sendMessage(TextUtils.format("<red>No permission!"))
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage(TextUtils.format("<red>Usage: /tournament start <id>"))
            return
        }
        
        val tournamentId = args[1]
        val tournament = plugin.tournamentManager.getTournament(tournamentId)
        
        if (tournament == null) {
            sender.sendMessage(TextUtils.format("<red>Tournament not found!"))
            return
        }
        
        plugin.tournamentManager.startTournament(tournamentId)
        sender.sendMessage(TextUtils.format("<green>✓ Tournament starting..."))
    }
    
    private fun handleList(sender: CommandSender) {
        val tournaments = plugin.tournamentManager.getAllTournaments()
        
        if (tournaments.isEmpty()) {
            sender.sendMessage(TextUtils.format("<yellow>No active tournaments"))
            return
        }
        
        sender.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══ TOURNAMENTS ═══</bold></gradient>"))
        tournaments.forEach { tournament ->
            val status = when (tournament.status) {
                TournamentStatus.WAITING -> "<green>WAITING"
                TournamentStatus.STARTING -> "<yellow>STARTING"
                TournamentStatus.IN_PROGRESS -> "<gold>IN PROGRESS"
                TournamentStatus.FINISHED -> "<gray>FINISHED"
            }
            sender.sendMessage(TextUtils.format("<yellow>${tournament.name}</yellow> <gray>- $status"))
            sender.sendMessage(TextUtils.format("  <gray>ID: <white>${tournament.id} <gray>| Kit: <white>${tournament.kitName}"))
            sender.sendMessage(TextUtils.format("  <gray>Players: <yellow>${tournament.participants.size}/${tournament.maxPlayers}"))
        }
    }
    
    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(TextUtils.format("<red>Usage: /tournament info <id>"))
            return
        }
        
        val tournament = plugin.tournamentManager.getTournament(args[1])
        
        if (tournament == null) {
            sender.sendMessage(TextUtils.format("<red>Tournament not found!"))
            return
        }
        
        sender.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══ ${tournament.name} ═══</bold></gradient>"))
        sender.sendMessage(TextUtils.format("<gray>ID: <yellow>${tournament.id}"))
        sender.sendMessage(TextUtils.format("<gray>Kit: <yellow>${tournament.kitName}"))
        sender.sendMessage(TextUtils.format("<gray>Status: <yellow>${tournament.status}"))
        sender.sendMessage(TextUtils.format("<gray>Players: <yellow>${tournament.participants.size}/${tournament.maxPlayers}"))
        sender.sendMessage(TextUtils.format("<gray>Bracket: <yellow>${tournament.bracketType}"))
        
        if (tournament.matches.isNotEmpty()) {
            sender.sendMessage(TextUtils.format("<gray>Matches: <yellow>${tournament.matches.size}"))
            val completed = tournament.matches.count { it.status == MatchStatus.COMPLETED }
            sender.sendMessage(TextUtils.format("<gray>Completed: <yellow>$completed/${tournament.matches.size}"))
        }
    }
    
    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══ TOURNAMENT COMMANDS ═══</bold></gradient>"))
        sender.sendMessage(TextUtils.format("<yellow>/tournament create <name> <kit> <max> [type]</yellow> <gray>- Create tournament"))
        sender.sendMessage(TextUtils.format("<yellow>/tournament join <id></yellow> <gray>- Join tournament"))
        sender.sendMessage(TextUtils.format("<yellow>/tournament leave</yellow> <gray>- Leave tournament"))
        sender.sendMessage(TextUtils.format("<yellow>/tournament start <id></yellow> <gray>- Start tournament"))
        sender.sendMessage(TextUtils.format("<yellow>/tournament list</yellow> <gray>- List tournaments"))
        sender.sendMessage(TextUtils.format("<yellow>/tournament info <id></yellow> <gray>- Tournament info"))
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("create", "join", "leave", "start", "list", "info")
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "join", "start", "info" -> plugin.tournamentManager.getAllTournaments()
                    .map { it.id }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                "create" -> listOf("<name>")
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "create" -> plugin.kitManager.getAllKits()
                    .map { it.name }
                    .filter { it.startsWith(args[2], ignoreCase = true) }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "create" -> listOf("2", "4", "8", "16", "32")
                else -> emptyList()
            }
            5 -> when (args[0].lowercase()) {
                "create" -> listOf("single", "double")
                    .filter { it.startsWith(args[4], ignoreCase = true) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
