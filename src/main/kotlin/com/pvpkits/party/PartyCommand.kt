package com.pvpkits.party

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Duel Challenge Commands
 */
class PartyCommand(private val plugin: PvPKitsPlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cOnly players can use duel commands!")
            return true
        }
        
        when (command.name.lowercase()) {
            "duel" -> handleDuel(sender, args)
            "challenge" -> handleChallenge(sender, args)
        }
        
        return true
    }
    
    private fun handleDuel(player: Player, args: Array<out String>) {
        if (args.isEmpty()) {
            sendDuelHelp(player)
            return
        }
        
        when (args[0].lowercase()) {
            "challenge" -> handleChallenge(player, args.drop(1).toTypedArray())
            "accept" -> handleAccept(player, args)
            "deny" -> handleDeny(player, args)
            "cancel" -> handleCancel(player)
            "list" -> handleList(player)
            "queue" -> handleQueue(player, args)
            "leave" -> handleLeaveQueue(player)
            else -> sendDuelHelp(player)
        }
    }
    
    private fun handleChallenge(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(TextUtils.format("<red>Usage: /duel challenge <player> <kit>"))
            player.sendMessage(TextUtils.format("<gray>Or: /challenge <player> <kit>"))
            return
        }
        
        val targetName = args[0]
        val kitName = args[1]
        
        val target = plugin.server.getPlayer(targetName) ?: run {
            player.sendMessage(TextUtils.format("<red>Player not found!"))
            return
        }
        
        plugin.partyManager.challengePlayer(player, target, kitName)
    }
    
    private fun handleAccept(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            // Show list of pending challenges
            val challenges = plugin.partyManager.getPendingChallenges(player)
            if (challenges.isEmpty()) {
                player.sendMessage(TextUtils.format("<red>You have no pending challenges!"))
                return
            }
            
            player.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══ PENDING CHALLENGES ═══</bold></gradient>"))
            challenges.forEach { challenge ->
                player.sendMessage(TextUtils.format("<yellow>${challenge.challengerName}</yellow> <gray>- Kit: <white>${challenge.kitName}"))
                player.sendMessage(TextUtils.format("  <green>/duel accept ${challenge.challengerName}"))
            }
            return
        }
        
        val challengerName = args[1]
        plugin.partyManager.acceptChallenge(player, challengerName)
    }
    
    private fun handleDeny(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(TextUtils.format("<red>Usage: /duel deny <player>"))
            return
        }
        
        val challengerName = args[1]
        plugin.partyManager.denyChallenge(player, challengerName)
    }
    
    private fun handleCancel(player: Player) {
        plugin.partyManager.cancelChallenge(player)
    }
    
    private fun handleList(player: Player) {
        val challenges = plugin.partyManager.getPendingChallenges(player)
        val activeChallenge = plugin.partyManager.getActiveChallenge(player)
        
        player.sendMessage("")
        player.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══ DUEL STATUS ═══</bold></gradient>"))
        
        if (activeChallenge != null) {
            player.sendMessage(TextUtils.format("<yellow>Your challenge:"))
            player.sendMessage(TextUtils.format("  <gray>To: <white>${activeChallenge.targetName}"))
            player.sendMessage(TextUtils.format("  <gray>Kit: <white>${activeChallenge.kitName}"))
            player.sendMessage(TextUtils.format("  <red>/duel cancel</red> <gray>to cancel"))
        }
        
        if (challenges.isNotEmpty()) {
            player.sendMessage(TextUtils.format("<yellow>Challenges from:"))
            challenges.forEach { challenge ->
                player.sendMessage(TextUtils.format("  <white>${challenge.challengerName} <gray>- <white>${challenge.kitName}"))
            }
            player.sendMessage(TextUtils.format("<green>/duel accept <player></green> <gray>to accept"))
        }
        
        if (activeChallenge == null && challenges.isEmpty()) {
            player.sendMessage(TextUtils.format("<gray>No active challenges"))
        }
        
        player.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══════════════════</bold></gradient>"))
        player.sendMessage("")
    }
    
    private fun handleQueue(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(TextUtils.format("<red>Usage: /duel queue <kit>"))
            return
        }
        
        val kitName = args[1]
        plugin.duelManager.joinQueue(player, kitName)
    }
    
    private fun handleLeaveQueue(player: Player) {
        plugin.duelManager.leaveQueue(player)
    }
    
    private fun sendDuelHelp(player: Player) {
        player.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══ DUEL COMMANDS ═══</bold></gradient>"))
        player.sendMessage(TextUtils.format("<yellow>/duel challenge <player> <kit></yellow> <gray>- Challenge player"))
        player.sendMessage(TextUtils.format("<yellow>/challenge <player> <kit></yellow> <gray>- Quick challenge"))
        player.sendMessage(TextUtils.format("<yellow>/duel accept <player></yellow> <gray>- Accept challenge"))
        player.sendMessage(TextUtils.format("<yellow>/duel deny <player></yellow> <gray>- Deny challenge"))
        player.sendMessage(TextUtils.format("<yellow>/duel cancel</yellow> <gray>- Cancel your challenge"))
        player.sendMessage(TextUtils.format("<yellow>/duel list</yellow> <gray>- List challenges"))
        player.sendMessage(TextUtils.format("<yellow>/duel queue <kit></yellow> <gray>- Join matchmaking"))
        player.sendMessage(TextUtils.format("<yellow>/duel leave</yellow> <gray>- Leave queue"))
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) return emptyList()
        
        return when (command.name.lowercase()) {
            "duel" -> when (args.size) {
                1 -> listOf("challenge", "accept", "deny", "cancel", "list", "queue", "leave")
                    .filter { it.startsWith(args[0], ignoreCase = true) }
                2 -> when (args[0].lowercase()) {
                    "challenge", "accept", "deny" -> plugin.server.onlinePlayers
                        .filter { it.uniqueId != sender.uniqueId }
                        .map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                    "queue" -> plugin.kitManager.getAllKits()
                        .map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                    else -> emptyList()
                }
                3 -> when (args[0].lowercase()) {
                    "challenge" -> plugin.kitManager.getAllKits()
                        .map { it.name }
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                    else -> emptyList()
                }
                else -> emptyList()
            }
            "challenge" -> when (args.size) {
                1 -> plugin.server.onlinePlayers
                    .filter { it.uniqueId != sender.uniqueId }
                    .map { it.name }
                    .filter { it.startsWith(args[0], ignoreCase = true) }
                2 -> plugin.kitManager.getAllKits()
                    .map { it.name }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cOnly players can use party commands!")
            return true
        }
        
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "create" -> handleCreate(sender)
            "invite" -> handleInvite(sender, args)
            "accept" -> handleAccept(sender)
            "deny" -> handleDeny(sender)
            "leave" -> handleLeave(sender)
            "kick" -> handleKick(sender, args)
            "disband" -> handleDisband(sender)
            "list" -> handleList(sender)
            "chat", "c" -> handleChat(sender, args)
            else -> sendHelp(sender)
        }
        
        return true
    }
    
    private fun handleCreate(player: Player) {
        if (plugin.partyManager.isInParty(player.uniqueId)) {
            player.sendMessage(TextUtils.format("<red>You are already in a party!"))
            return
        }
        
        plugin.partyManager.createParty(player)
    }
    
    private fun handleInvite(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(TextUtils.format("<red>Usage: /party invite <player>"))
            return
        }
        
        val party = plugin.partyManager.getPlayerParty(player.uniqueId) ?: run {
            player.sendMessage(TextUtils.format("<red>You are not in a party! Create one with /party create"))
            return
        }
        
        val target = plugin.server.getPlayer(args[1]) ?: run {
            player.sendMessage(TextUtils.format("<red>Player not found!"))
            return
        }
        
        plugin.partyManager.invitePlayer(party, player, target)
    }
    
    private fun handleAccept(player: Player) {
        plugin.partyManager.acceptInvite(player)
    }
    
    private fun handleDeny(player: Player) {
        plugin.partyManager.denyInvite(player)
    }
    
    private fun handleLeave(player: Player) {
        plugin.partyManager.leaveParty(player)
    }
    
    private fun handleKick(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(TextUtils.format("<red>Usage: /party kick <player>"))
            return
        }
        
        val target = plugin.server.getPlayer(args[1]) ?: run {
            player.sendMessage(TextUtils.format("<red>Player not found!"))
            return
        }
        
        plugin.partyManager.kickPlayer(player, target)
    }
    
    private fun handleDisband(player: Player) {
        val party = plugin.partyManager.getPlayerParty(player.uniqueId) ?: run {
            player.sendMessage(TextUtils.format("<red>You are not in a party!"))
            return
        }
        
        if (party.leader != player.uniqueId) {
            player.sendMessage(TextUtils.format("<red>Only the party leader can disband the party!"))
            return
        }
        
        plugin.partyManager.disbandParty(party)
    }
    
    private fun handleList(player: Player) {
        val party = plugin.partyManager.getPlayerParty(player.uniqueId) ?: run {
            player.sendMessage(TextUtils.format("<red>You are not in a party!"))
            return
        }
        
        val members = plugin.partyManager.getOnlineMembers(party)
        val leader = plugin.server.getPlayer(party.leader)
        
        player.sendMessage("")
        player.sendMessage(TextUtils.format("<gradient:#00ff00:#00aa00><bold>═══ PARTY MEMBERS ═══</bold></gradient>"))
        player.sendMessage(TextUtils.format("<gray>Leader: <yellow>${leader?.name ?: "Unknown"} <gold>★"))
        player.sendMessage(TextUtils.format("<gray>Members <yellow>(${party.members.size}/${party.maxSize})</yellow>:"))
        
        members.forEach { member ->
            if (member.uniqueId != party.leader) {
                player.sendMessage(TextUtils.format("  <gray>• <yellow>${member.name}"))
            }
        }
        
        player.sendMessage(TextUtils.format("<gradient:#00ff00:#00aa00><bold>═══════════════════</bold></gradient>"))
        player.sendMessage("")
    }
    
    private fun handleChat(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(TextUtils.format("<red>Usage: /party chat <message>"))
            return
        }
        
        val message = args.drop(1).joinToString(" ")
        plugin.partyManager.sendPartyChat(player, message)
    }
    
    private fun sendHelp(player: Player) {
        player.sendMessage(TextUtils.format("<gradient:#00ff00:#00aa00><bold>═══ PARTY COMMANDS ═══</bold></gradient>"))
        player.sendMessage(TextUtils.format("<yellow>/party create</yellow> <gray>- Create a party"))
        player.sendMessage(TextUtils.format("<yellow>/party invite <player></yellow> <gray>- Invite player"))
        player.sendMessage(TextUtils.format("<yellow>/party accept</yellow> <gray>- Accept invite"))
        player.sendMessage(TextUtils.format("<yellow>/party deny</yellow> <gray>- Deny invite"))
        player.sendMessage(TextUtils.format("<yellow>/party leave</yellow> <gray>- Leave party"))
        player.sendMessage(TextUtils.format("<yellow>/party kick <player></yellow> <gray>- Kick player"))
        player.sendMessage(TextUtils.format("<yellow>/party disband</yellow> <gray>- Disband party"))
        player.sendMessage(TextUtils.format("<yellow>/party list</yellow> <gray>- List members"))
        player.sendMessage(TextUtils.format("<yellow>/party chat <msg></yellow> <gray>- Party chat"))
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) return emptyList()
        
        return when (args.size) {
            1 -> listOf("create", "invite", "accept", "deny", "leave", "kick", "disband", "list", "chat")
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "invite", "kick" -> plugin.server.onlinePlayers
                    .filter { it.uniqueId != sender.uniqueId }
                    .map { it.name }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
