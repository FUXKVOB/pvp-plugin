package com.pvpkits.replay

import com.github.shynixn.mccoroutine.bukkit.launch
import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.ComponentCache
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Replay Viewer GUI - 2026 Edition
 * 
 * Просмотр реплеев через GUI:
 * - Список последних реплеев
 * - Информация о матче
 * - Воспроизведение (будущая фича)
 */
class ReplayViewerGUI(private val plugin: PvPKitsPlugin) {
    
    /**
     * Открыть список реплеев игрока
     */
    fun openReplayList(player: Player) {
        plugin.launch {
            val replayIds = plugin.replayManager.getPlayerReplays(player.uniqueId)
            
            if (replayIds.isEmpty()) {
                player.sendMessage(ComponentCache.parse("<red>У вас нет сохраненных реплеев"))
                return@launch
            }
            
            val inventory = Bukkit.createInventory(
                null,
                54,
                ComponentCache.parse("<gradient:#ff0000:#ff6b6b>📹 Мои Реплеи</gradient>")
            )
            
            replayIds.take(45).forEachIndexed { index, replayId ->
                val replay = plugin.replayManager.loadReplay(replayId)
                if (replay != null) {
                    val item = createReplayItem(replay)
                    inventory.setItem(index, item)
                }
            }
            
            // Кнопка закрытия
            val closeButton = ItemStack(Material.BARRIER)
            val closeMeta = closeButton.itemMeta
            closeMeta.displayName(ComponentCache.parse("<red><bold>Закрыть"))
            closeButton.itemMeta = closeMeta
            inventory.setItem(49, closeButton)
            
            player.openInventory(inventory)
        }
    }
    
    /**
     * Создать предмет реплея
     */
    private fun createReplayItem(replay: ReplayData): ItemStack {
        val item = ItemStack(Material.ENDER_EYE)
        val meta = item.itemMeta
        
        // Название
        val player1Name = Bukkit.getOfflinePlayer(replay.player1).name ?: "Unknown"
        val player2Name = Bukkit.getOfflinePlayer(replay.player2).name ?: "Unknown"
        val winnerName = Bukkit.getOfflinePlayer(replay.winner).name ?: "Unknown"
        
        meta.displayName(ComponentCache.parse(
            "<gold><bold>$player1Name <gray>vs <gold><bold>$player2Name"
        ))
        
        // Лор
        val lore = mutableListOf<Component>()
        lore.add(Component.empty())
        lore.add(ComponentCache.parse("<gray>Кит: <yellow>${replay.kitName}"))
        lore.add(ComponentCache.parse("<gray>Победитель: <green>$winnerName"))
        lore.add(ComponentCache.parse("<gray>Длительность: <aqua>${formatDuration(replay.duration)}"))
        lore.add(ComponentCache.parse("<gray>Фреймов: <white>${replay.frames.size}"))
        lore.add(Component.empty())
        lore.add(ComponentCache.parse("<gray>ID: <dark_gray>${replay.id.take(8)}..."))
        lore.add(Component.empty())
        lore.add(ComponentCache.parse("<yellow>▶ Нажмите для просмотра"))
        lore.add(ComponentCache.parse("<red>⚠ Просмотр в разработке"))
        
        meta.lore(lore)
        item.itemMeta = meta
        
        return item
    }
    
    /**
     * Форматировать длительность
     */
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        
        return if (minutes > 0) {
            "${minutes}м ${remainingSeconds}с"
        } else {
            "${remainingSeconds}с"
        }
    }
    
    /**
     * Воспроизвести реплей (будущая фича)
     */
    fun playReplay(player: Player, replayId: String) {
        plugin.launch {
            val replay = plugin.replayManager.loadReplay(replayId)
            
            if (replay == null) {
                player.sendMessage(ComponentCache.parse("<red>Реплей не найден"))
                return@launch
            }
            
            player.sendMessage(ComponentCache.parse("<yellow>⚠ Просмотр реплеев в разработке"))
            player.sendMessage(ComponentCache.parse("<gray>Реплей: ${replay.id}"))
            player.sendMessage(ComponentCache.parse("<gray>Фреймов: ${replay.frames.size}"))
        }
        
        // TODO: Реализовать воспроизведение
        // - Телепортировать игрока на арену
        // - Режим наблюдателя
        // - Воспроизведение фреймов
        // - Управление (пауза, перемотка)
    }
}
