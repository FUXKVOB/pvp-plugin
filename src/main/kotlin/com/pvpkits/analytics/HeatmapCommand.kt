package com.pvpkits.analytics

import com.pvpkits.PvPKitsPlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Команды для heatmap
 */
class HeatmapCommand(private val plugin: PvPKitsPlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cЭта команда только для игроков!")
            return true
        }
        
        if (!sender.hasPermission("pvpkits.heatmap")) {
            sender.sendMessage("§cНет прав!")
            return true
        }
        
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "deaths" -> showDeathHeatmap(sender, args)
            "kills" -> showKillHeatmap(sender, args)
            "stats" -> showStats(sender, args)
            "clear" -> clearData(sender, args)
            else -> sendHelp(sender)
        }
        
        return true
    }
    
    private fun sendHelp(player: Player) {
        player.sendMessage("")
        player.sendMessage("§6═══════ Heatmap Commands ═══════")
        player.sendMessage("§e/heatmap deaths <arena> [duration] §7- Показать зоны смертей")
        player.sendMessage("§e/heatmap kills <arena> [duration] §7- Показать зоны убийств")
        player.sendMessage("§e/heatmap stats <arena> §7- Статистика арены")
        player.sendMessage("§e/heatmap clear <arena> §7- Очистить данные")
        player.sendMessage("§6═══════════════════════════════")
        player.sendMessage("")
    }
    
    private fun showDeathHeatmap(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("§cИспользование: /heatmap deaths <arena> [duration]")
            return
        }
        
        val arenaName = args[1]
        val duration = if (args.size >= 3) args[2].toIntOrNull() ?: 30 else 30
        
        plugin.heatmapManager.visualizeDeathHeatmap(player, arenaName, duration)
    }
    
    private fun showKillHeatmap(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("§cИспользование: /heatmap kills <arena> [duration]")
            return
        }
        
        val arenaName = args[1]
        val duration = if (args.size >= 3) args[2].toIntOrNull() ?: 30 else 30
        
        plugin.heatmapManager.visualizeKillHeatmap(player, arenaName, duration)
    }
    
    private fun showStats(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage("§cИспользование: /heatmap stats <arena>")
            return
        }
        
        val arenaName = args[1]
        val stats = plugin.heatmapManager.getArenaStats(arenaName)
        
        player.sendMessage("")
        player.sendMessage(stats)
        player.sendMessage("")
        
        // Показать топ опасных зон
        val topZones = plugin.heatmapManager.getTopDangerZones(arenaName, 3)
        if (topZones.isNotEmpty()) {
            player.sendMessage("§eТоп-3 опасных зон:")
            topZones.forEachIndexed { index, zone ->
                player.sendMessage("§7${index + 1}. §fX: ${zone.x} Z: ${zone.z} §7- §c${zone.count} смертей")
            }
            player.sendMessage("")
        }
    }
    
    private fun clearData(player: Player, args: Array<out String>) {
        if (!player.hasPermission("pvpkits.heatmap.clear")) {
            player.sendMessage("§cНет прав!")
            return
        }
        
        if (args.size < 2) {
            player.sendMessage("§cИспользование: /heatmap clear <arena>")
            return
        }
        
        val arenaName = args[1]
        plugin.heatmapManager.clearArenaData(arenaName)
        player.sendMessage("§aДанные арены §f$arenaName §aочищены")
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("deaths", "kills", "stats", "clear")
                .filter { it.startsWith(args[0], ignoreCase = true) }
            
            2 -> {
                // Список арен
                plugin.arenaManager.getAllArenas().map { it.name }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
            }
            
            3 -> {
                if (args[0].equals("deaths", ignoreCase = true) || 
                    args[0].equals("kills", ignoreCase = true)) {
                    listOf("10", "30", "60")
                } else emptyList()
            }
            
            else -> emptyList()
        }
    }
}
