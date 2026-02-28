package com.pvpkits.analytics

import com.pvpkits.PvPKitsPlugin
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Particle.DustOptions
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Heatmap Manager - аналитика арен
 * 
 * Отслеживает:
 * - Локации смертей
 * - Локации убийств
 * - Популярные зоны боя
 * - Статистика по аренам
 */
class HeatmapManager(private val plugin: PvPKitsPlugin) {
    
    private val deathLocations = ConcurrentHashMap<String, MutableList<LocationData>>()
    private val killLocations = ConcurrentHashMap<String, MutableList<LocationData>>()
    private val combatZones = ConcurrentHashMap<String, MutableList<LocationData>>()
    
    data class LocationData(
        val x: Int,
        val y: Int,
        val z: Int,
        val timestamp: Long,
        val playerUUID: UUID
    )
    
    data class HeatmapCell(
        val x: Int,
        val z: Int,
        val count: Int,
        val intensity: Float // 0.0 - 1.0
    )
    
    companion object {
        private const val CELL_SIZE = 4 // 4x4 блока на ячейку
        private const val MAX_HISTORY = 1000 // Максимум записей на арену
    }
    
    /**
     * Записать смерть
     */
    fun recordDeath(arenaName: String, location: Location, playerUUID: UUID) {
        val data = LocationData(
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ,
            timestamp = System.currentTimeMillis(),
            playerUUID = playerUUID
        )
        
        val list = deathLocations.getOrPut(arenaName) { mutableListOf() }
        list.add(data)
        
        // Ограничиваем размер
        if (list.size > MAX_HISTORY) {
            list.removeAt(0)
        }
    }
    
    /**
     * Записать убийство
     */
    fun recordKill(arenaName: String, location: Location, playerUUID: UUID) {
        val data = LocationData(
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ,
            timestamp = System.currentTimeMillis(),
            playerUUID = playerUUID
        )
        
        val list = killLocations.getOrPut(arenaName) { mutableListOf() }
        list.add(data)
        
        if (list.size > MAX_HISTORY) {
            list.removeAt(0)
        }
    }
    
    /**
     * Записать зону боя
     */
    fun recordCombatZone(arenaName: String, location: Location, playerUUID: UUID) {
        val data = LocationData(
            x = location.blockX,
            y = location.blockY,
            z = location.blockZ,
            timestamp = System.currentTimeMillis(),
            playerUUID = playerUUID
        )
        
        val list = combatZones.getOrPut(arenaName) { mutableListOf() }
        list.add(data)
        
        if (list.size > MAX_HISTORY * 2) {
            list.removeAt(0)
        }
    }
    
    /**
     * Получить heatmap смертей
     */
    fun getDeathHeatmap(arenaName: String): List<HeatmapCell> {
        val locations = deathLocations[arenaName] ?: return emptyList()
        return generateHeatmap(locations)
    }
    
    /**
     * Получить heatmap убийств
     */
    fun getKillHeatmap(arenaName: String): List<HeatmapCell> {
        val locations = killLocations[arenaName] ?: return emptyList()
        return generateHeatmap(locations)
    }
    
    /**
     * Получить heatmap зон боя
     */
    fun getCombatHeatmap(arenaName: String): List<HeatmapCell> {
        val locations = combatZones[arenaName] ?: return emptyList()
        return generateHeatmap(locations)
    }
    
    /**
     * Генерация heatmap из локаций
     */
    private fun generateHeatmap(locations: List<LocationData>): List<HeatmapCell> {
        if (locations.isEmpty()) return emptyList()
        
        // Группируем по ячейкам
        val cellCounts = locations
            .groupingBy { Pair(it.x / CELL_SIZE, it.z / CELL_SIZE) }
            .eachCount()
        
        if (cellCounts.isEmpty()) return emptyList()
        
        // Находим максимум для нормализации
        val maxCount = cellCounts.values.maxOrNull() ?: 1
        
        // Создаем ячейки с интенсивностью
        return cellCounts.map { (cell, count) ->
            HeatmapCell(
                x = cell.first * CELL_SIZE,
                z = cell.second * CELL_SIZE,
                count = count,
                intensity = count.toFloat() / maxCount
            )
        }.sortedByDescending { it.count }
    }
    
    /**
     * Визуализировать heatmap частицами
     */
    fun visualizeDeathHeatmap(player: Player, arenaName: String, duration: Int = 30) {
        val heatmap = getDeathHeatmap(arenaName)
        
        if (heatmap.isEmpty()) {
            player.sendMessage("§cНет данных для отображения")
            return
        }
        
        player.sendMessage("§aОтображение heatmap смертей на $duration секунд...")
        
        // Показываем частицы
        var ticks = 0
        val maxTicks = duration * 20
        
        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (ticks >= maxTicks) return@Runnable
            
            heatmap.take(50).forEach { cell ->
                val location = Location(
                    player.world,
                    cell.x.toDouble() + CELL_SIZE / 2.0,
                    player.location.y,
                    cell.z.toDouble() + CELL_SIZE / 2.0
                )
                
                // Цвет зависит от интенсивности
                val color = getHeatColor(cell.intensity)
                val size = 1.0f + cell.intensity * 2.0f
                
                player.spawnParticle(
                    Particle.DUST,
                    location,
                    5,
                    0.5, 0.1, 0.5,
                    DustOptions(color, size)
                )
            }
            
            ticks++
        }, 0L, 20L)
        
        // Отменяем через duration секунд
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            task.cancel()
            player.sendMessage("§eHeatmap скрыт")
        }, maxTicks.toLong())
    }
    
    /**
     * Визуализировать heatmap убийств
     */
    fun visualizeKillHeatmap(player: Player, arenaName: String, duration: Int = 30) {
        val heatmap = getKillHeatmap(arenaName)
        
        if (heatmap.isEmpty()) {
            player.sendMessage("§cНет данных для отображения")
            return
        }
        
        player.sendMessage("§aОтображение heatmap убийств на $duration секунд...")
        
        var ticks = 0
        val maxTicks = duration * 20
        
        val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (ticks >= maxTicks) return@Runnable
            
            heatmap.take(50).forEach { cell ->
                val location = Location(
                    player.world,
                    cell.x.toDouble() + CELL_SIZE / 2.0,
                    player.location.y,
                    cell.z.toDouble() + CELL_SIZE / 2.0
                )
                
                val color = getKillHeatColor(cell.intensity)
                val size = 1.0f + cell.intensity * 2.0f
                
                player.spawnParticle(
                    Particle.DUST,
                    location,
                    5,
                    0.5, 0.1, 0.5,
                    DustOptions(color, size)
                )
            }
            
            ticks++
        }, 0L, 20L)
        
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            task.cancel()
            player.sendMessage("§eHeatmap скрыт")
        }, maxTicks.toLong())
    }
    
    /**
     * Получить цвет для heatmap (смерти)
     */
    private fun getHeatColor(intensity: Float): Color {
        return when {
            intensity > 0.8f -> Color.fromRGB(255, 0, 0) // Красный
            intensity > 0.6f -> Color.fromRGB(255, 100, 0) // Оранжевый
            intensity > 0.4f -> Color.fromRGB(255, 200, 0) // Желтый
            intensity > 0.2f -> Color.fromRGB(200, 255, 0) // Желто-зеленый
            else -> Color.fromRGB(0, 255, 0) // Зеленый
        }
    }
    
    /**
     * Получить цвет для heatmap (убийства)
     */
    private fun getKillHeatColor(intensity: Float): Color {
        return when {
            intensity > 0.8f -> Color.fromRGB(200, 0, 255) // Фиолетовый
            intensity > 0.6f -> Color.fromRGB(150, 0, 255) // Темно-фиолетовый
            intensity > 0.4f -> Color.fromRGB(100, 100, 255) // Синий
            intensity > 0.2f -> Color.fromRGB(0, 200, 255) // Голубой
            else -> Color.fromRGB(0, 255, 200) // Бирюзовый
        }
    }
    
    /**
     * Получить статистику арены
     */
    fun getArenaStats(arenaName: String): String {
        val deaths = deathLocations[arenaName]?.size ?: 0
        val kills = killLocations[arenaName]?.size ?: 0
        val combats = combatZones[arenaName]?.size ?: 0
        
        val deathHeatmap = getDeathHeatmap(arenaName)
        val hotspot = deathHeatmap.firstOrNull()
        
        return buildString {
            append("§6═══════════════════════════\n")
            append("§eСтатистика арены: §f$arenaName\n")
            append("§6═══════════════════════════\n")
            append("§7Смертей: §c$deaths\n")
            append("§7Убийств: §a$kills\n")
            append("§7Зон боя: §e$combats\n")
            
            if (hotspot != null) {
                append("\n§7Самая опасная зона:\n")
                append("§7  X: §f${hotspot.x} §7Z: §f${hotspot.z}\n")
                append("§7  Смертей: §c${hotspot.count}\n")
            }
            
            append("§6═══════════════════════════")
        }
    }
    
    /**
     * Получить топ опасных зон
     */
    fun getTopDangerZones(arenaName: String, limit: Int = 5): List<HeatmapCell> {
        return getDeathHeatmap(arenaName).take(limit)
    }
    
    /**
     * Очистить данные арены
     */
    fun clearArenaData(arenaName: String) {
        deathLocations.remove(arenaName)
        killLocations.remove(arenaName)
        combatZones.remove(arenaName)
    }
    
    /**
     * Получить общую статистику
     */
    fun getGlobalStats(): Map<String, Any> {
        return mapOf(
            "tracked_arenas" to deathLocations.keys.size,
            "total_deaths" to deathLocations.values.sumOf { it.size },
            "total_kills" to killLocations.values.sumOf { it.size },
            "total_combat_zones" to combatZones.values.sumOf { it.size }
        )
    }
}
