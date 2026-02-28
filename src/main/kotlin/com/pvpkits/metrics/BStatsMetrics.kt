package com.pvpkits.metrics

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.FoliaSchedulerUtils
import org.bstats.bukkit.Metrics
import org.bstats.charts.AdvancedPie
import org.bstats.charts.SimplePie
import org.bstats.charts.SingleLineChart

/**
 * bStats Metrics Integration - 2026 Edition
 * 
 * Мониторинг использования плагина:
 * - Тип сервера (Paper/Folia)
 * - Активные дуэли
 * - Использование китов
 * - Количество игроков
 */
class BStatsMetrics(private val plugin: PvPKitsPlugin) {
    
    private val pluginId = 12345 // TODO: Получить на https://bstats.org/
    
    fun initialize() {
        val metrics = Metrics(plugin, pluginId)
        
        // Тип сервера
        metrics.addCustomChart(SimplePie("server_type") {
            if (FoliaSchedulerUtils.isFoliaServer()) "Folia" else "Paper"
        })
        
        // Активные дуэли
        metrics.addCustomChart(SingleLineChart("active_duels") {
            plugin.duelManager.getActiveMatchCount()
        })
        
        // Игроки в очереди
        metrics.addCustomChart(SingleLineChart("players_in_queue") {
            plugin.duelManager.getTotalInQueues()
        })
        
        // Использование китов
        metrics.addCustomChart(AdvancedPie("kit_usage") {
            val usage = mutableMapOf<String, Int>()
            
            plugin.server.onlinePlayers.forEach { player ->
                val stats = plugin.statsManager.getStatsIfExists(player.uniqueId)
                stats?.lastKitUsed?.let { kit ->
                    usage[kit] = usage.getOrDefault(kit, 0) + 1
                }
            }
            
            usage
        })
        
        // Количество игроков
        metrics.addCustomChart(SingleLineChart("total_players") {
            plugin.statsManager.getTotalPlayers()
        })
        
        // Включен ли ELO рейтинг
        metrics.addCustomChart(SimplePie("elo_enabled") {
            if (plugin.config.getBoolean("rating.enabled", true)) "Enabled" else "Disabled"
        })
        
        // Включены ли реплеи
        metrics.addCustomChart(SimplePie("replays_enabled") {
            if (plugin.config.getBoolean("replay.enabled", true)) "Enabled" else "Disabled"
        })
        
        // Включена ли косметика
        metrics.addCustomChart(SimplePie("cosmetics_enabled") {
            if (plugin.config.getBoolean("cosmetics.enabled", true)) "Enabled" else "Disabled"
        })
        
        plugin.logger.info("bStats metrics initialized")
    }
}
