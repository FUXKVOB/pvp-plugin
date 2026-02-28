package com.pvpkits.spectator

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class SpectatorCommand(private val plugin: PvPKitsPlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cOnly players can use this command!")
            return true
        }
        
        when (command.name.lowercase()) {
            "spectate", "spec" -> handleSpectate(sender, args)
            "stopspectating", "stopspec" -> handleStopSpectating(sender)
        }
        
        return true
    }
    
    private fun handleSpectate(player: Player, args: Array<out String>) {
        if (args.isEmpty()) {
            player.sendMessage(TextUtils.format("<red>Usage: /spectate <player>"))
            return
        }
        
        val targetName = args[0]
        val target = plugin.server.getPlayer(targetName)
        
        if (target == null) {
            player.sendMessage(TextUtils.format("<red>Player not found!"))
            return
        }
        
        if (target.uniqueId == player.uniqueId) {
            player.sendMessage(TextUtils.format("<red>You cannot spectate yourself!"))
            return
        }
        
        // Check if target is in a match
        val inDuel = plugin.duelManager.isInMatch(target.uniqueId)
        val inArena = plugin.arenaManager.isInArena(target)
        
        if (!inDuel && !inArena) {
            player.sendMessage(TextUtils.format("<red>This player is not in a match!"))
            return
        }
        
        if (plugin.spectatorManager.startSpectating(player, target)) {
            player.sendMessage(TextUtils.format("<green>Now spectating <yellow>${target.name}"))
            player.sendMessage(TextUtils.format("<gray>Use <yellow>/stopspec</yellow> to stop spectating"))
            
            // Show spectator count to target
            val count = plugin.spectatorManager.getSpectatorCount(target)
            if (count > 0) {
                target.sendMessage(TextUtils.format("<gray>👁 <yellow>$count</yellow> spectator(s) watching"))
            }
        } else {
            player.sendMessage(TextUtils.format("<red>Failed to start spectating!"))
        }
    }
    
    private fun handleStopSpectating(player: Player) {
        if (!plugin.spectatorManager.isSpectating(player)) {
            player.sendMessage(TextUtils.format("<red>You are not spectating anyone!"))
            return
        }
        
        if (plugin.spectatorManager.stopSpectating(player)) {
            player.sendMessage(TextUtils.format("<green>Stopped spectating"))
        } else {
            player.sendMessage(TextUtils.format("<red>Failed to stop spectating!"))
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
            "spectate", "spec" -> {
                if (args.size == 1) {
                    // Show players in matches
                    plugin.server.onlinePlayers
                        .filter { it.uniqueId != sender.uniqueId }
                        .filter { 
                            plugin.duelManager.isInMatch(it.uniqueId) || 
                            plugin.arenaManager.isInArena(it) 
                        }
                        .map { it.name }
                        .filter { it.startsWith(args[0], ignoreCase = true) }
                } else emptyList()
            }
            else -> emptyList()
        }
    }
}
