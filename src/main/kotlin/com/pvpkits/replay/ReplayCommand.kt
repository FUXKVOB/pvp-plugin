package com.pvpkits.replay

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.ComponentCache
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Replay Command - просмотр реплеев
 */
class ReplayCommand(private val plugin: PvPKitsPlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cТолько игроки могут использовать эту команду")
            return true
        }
        
        if (!plugin.config.getBoolean("replay.enabled", true)) {
            sender.sendMessage(ComponentCache.parse("<red>Система реплеев отключена"))
            return true
        }
        
        when (args.getOrNull(0)?.lowercase()) {
            "list", null -> {
                // Открыть GUI со списком реплеев
                plugin.replayViewerGUI.openReplayList(sender)
            }
            
            "view", "play" -> {
                if (args.size < 2) {
                    sender.sendMessage(ComponentCache.parse("<red>Использование: /replay view <id>"))
                    return true
                }
                
                val replayId = args[1]
                plugin.replayViewerGUI.playReplay(sender, replayId)
            }
            
            "info" -> {
                val replays = plugin.replayManager.getPlayerReplays(sender.uniqueId)
                sender.sendMessage(ComponentCache.parse("<gold><bold>📹 Мои Реплеи"))
                sender.sendMessage(ComponentCache.parse("<gray>Всего реплеев: <yellow>${replays.size}"))
                sender.sendMessage(ComponentCache.parse("<gray>Максимум: <yellow>${plugin.config.getInt("replay.max-replays-per-player", 10)}"))
                sender.sendMessage(ComponentCache.parse("<gray>Используйте <yellow>/replay list <gray>для просмотра"))
            }
            
            else -> {
                sender.sendMessage(ComponentCache.parse("<gold><bold>📹 Replay Команды"))
                sender.sendMessage(ComponentCache.parse("<yellow>/replay list <gray>- Список ваших реплеев"))
                sender.sendMessage(ComponentCache.parse("<yellow>/replay view <id> <gray>- Просмотр реплея"))
                sender.sendMessage(ComponentCache.parse("<yellow>/replay info <gray>- Информация о реплеях"))
            }
        }
        
        return true
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("list", "view", "info").filter { it.startsWith(args[0].lowercase()) }
        }
        
        if (args.size == 2 && args[0].lowercase() in listOf("view", "play")) {
            if (sender is Player) {
                val replays = plugin.replayManager.getPlayerReplays(sender.uniqueId)
                return replays.map { it.take(8) }
            }
        }
        
        return emptyList()
    }
}
