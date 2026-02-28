package com.pvpkits.matchmaking

import com.pvpkits.PvPKitsPlugin
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * MMR-based matchmaking система
 * 
 * Подбирает противников по близкому рейтингу для честных матчей
 */
class MatchmakingManager(private val plugin: PvPKitsPlugin) {
    
    private val queues = ConcurrentHashMap<String, MutableList<QueueEntry>>()
    
    data class QueueEntry(
        val uuid: UUID,
        val playerName: String,
        val rating: Int,
        val queueTime: Long,
        val kitName: String
    )
    
    companion object {
        private const val BASE_RATING_RANGE = 100 // ±100 рейтинга базово
        private const val RANGE_EXPANSION_PER_10S = 50 // +50 каждые 10 секунд
        private const val MAX_RATING_RANGE = 500 // Максимум ±500
        private const val QUEUE_CHECK_INTERVAL = 20L // Проверка каждую секунду
    }
    
    init {
        // Периодическая проверка очереди
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            checkAllQueues()
        }, QUEUE_CHECK_INTERVAL, QUEUE_CHECK_INTERVAL)
    }
    
    /**
     * Добавить игрока в очередь с MMR
     */
    fun addToQueue(player: Player, kitName: String): Boolean {
        val uuid = player.uniqueId
        
        // Проверка что не в очереди уже
        if (isInQueue(uuid)) {
            player.sendMessage("§cВы уже в очереди!")
            return false
        }
        
        // Проверка что не в матче
        if (plugin.duelManager.isInMatch(uuid)) {
            player.sendMessage("§cВы уже в матче!")
            return false
        }
        
        val rating = plugin.ratingManager.getRating(uuid)?.rating ?: 1000
        val entry = QueueEntry(
            uuid = uuid,
            playerName = player.name,
            rating = rating,
            queueTime = System.currentTimeMillis(),
            kitName = kitName
        )
        
        val queue = queues.getOrPut(kitName) { mutableListOf() }
        queue.add(entry)
        
        player.sendMessage("")
        player.sendMessage("§6═══════════════════════════")
        player.sendMessage("§e⚔ Поиск противника...")
        player.sendMessage("§7Кит: §f$kitName")
        player.sendMessage("§7Ваш рейтинг: §e$rating")
        player.sendMessage("§7Диапазон поиска: §a±$BASE_RATING_RANGE")
        player.sendMessage("§6═══════════════════════════")
        player.sendMessage("")
        
        // Сразу попробовать найти матч
        tryFindMatch(entry)
        
        return true
    }
    
    /**
     * Убрать игрока из очереди
     */
    fun removeFromQueue(player: Player): Boolean {
        val uuid = player.uniqueId
        var removed = false
        
        queues.values.forEach { queue ->
            if (queue.removeIf { it.uuid == uuid }) {
                removed = true
            }
        }
        
        if (removed) {
            player.sendMessage("§eВы покинули очередь")
        }
        
        return removed
    }
    
    /**
     * Проверить все очереди на возможные матчи
     */
    private fun checkAllQueues() {
        queues.forEach { (kitName, queue) ->
            if (queue.size < 2) return@forEach
            
            // Сортируем по времени ожидания (старые первыми)
            queue.sortBy { it.queueTime }
            
            // Пытаемся найти матчи для каждого
            val toRemove = mutableListOf<QueueEntry>()
            
            queue.forEach { entry ->
                if (toRemove.contains(entry)) return@forEach
                
                val opponent = findBestMatch(entry, queue)
                if (opponent != null) {
                    toRemove.add(entry)
                    toRemove.add(opponent)
                    
                    // Запускаем матч
                    val player1 = plugin.server.getPlayer(entry.uuid)
                    val player2 = plugin.server.getPlayer(opponent.uuid)
                    
                    if (player1 != null && player2 != null) {
                        startMatch(player1, player2, kitName, entry.rating, opponent.rating)
                    }
                }
            }
            
            // Удаляем из очереди
            queue.removeAll(toRemove)
        }
    }
    
    /**
     * Попытка найти матч для игрока
     */
    private fun tryFindMatch(entry: QueueEntry) {
        val queue = queues[entry.kitName] ?: return
        
        val opponent = findBestMatch(entry, queue)
        if (opponent != null) {
            queue.remove(entry)
            queue.remove(opponent)
            
            val player1 = plugin.server.getPlayer(entry.uuid)
            val player2 = plugin.server.getPlayer(opponent.uuid)
            
            if (player1 != null && player2 != null) {
                startMatch(player1, player2, entry.kitName, entry.rating, opponent.rating)
            }
        }
    }
    
    /**
     * Найти лучшего противника по MMR
     */
    private fun findBestMatch(entry: QueueEntry, queue: List<QueueEntry>): QueueEntry? {
        val waitTime = System.currentTimeMillis() - entry.queueTime
        val ratingRange = calculateRatingRange(waitTime)
        
        // Ищем ближайшего по рейтингу в допустимом диапазоне
        return queue
            .filter { it.uuid != entry.uuid }
            .filter { abs(it.rating - entry.rating) <= ratingRange }
            .minByOrNull { abs(it.rating - entry.rating) }
    }
    
    /**
     * Рассчитать диапазон поиска (расширяется со временем)
     */
    private fun calculateRatingRange(waitTimeMs: Long): Int {
        val expansion = (waitTimeMs / 10000) * RANGE_EXPANSION_PER_10S
        return (BASE_RATING_RANGE + expansion).toInt().coerceAtMost(MAX_RATING_RANGE)
    }
    
    /**
     * Запустить матч между игроками
     */
    private fun startMatch(
        player1: Player, 
        player2: Player, 
        kitName: String,
        rating1: Int,
        rating2: Int
    ) {
        val ratingDiff = abs(rating1 - rating2)
        
        // Уведомление о найденном матче
        listOf(player1, player2).forEach { player ->
            player.sendMessage("")
            player.sendMessage("§a§l═══════════════════════════")
            player.sendMessage("§e§l⚔ МАТЧ НАЙДЕН!")
            player.sendMessage("")
            player.sendMessage("§f${player1.name} §7(§e$rating1§7) §fvs §f${player2.name} §7(§e$rating2§7)")
            player.sendMessage("§7Разница рейтинга: §e$ratingDiff")
            player.sendMessage("§7Кит: §f$kitName")
            player.sendMessage("§a§l═══════════════════════════")
            player.sendMessage("")
            
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        }
        
        // Запускаем дуэль через DuelManager
        plugin.duelManager.startDirectDuel(player1, player2, kitName)
    }
    
    /**
     * Проверить в очереди ли игрок
     */
    fun isInQueue(uuid: UUID): Boolean {
        return queues.values.any { queue -> queue.any { it.uuid == uuid } }
    }
    
    /**
     * Получить позицию в очереди
     */
    fun getQueuePosition(uuid: UUID): Int? {
        queues.values.forEach { queue ->
            val index = queue.indexOfFirst { it.uuid == uuid }
            if (index != -1) return index + 1
        }
        return null
    }
    
    /**
     * Получить информацию об очереди
     */
    fun getQueueInfo(kitName: String): String {
        val queue = queues[kitName] ?: return "§cОчередь пуста"
        
        if (queue.isEmpty()) return "§eНикого в очереди"
        
        return buildString {
            append("§6═══════════════════════════\n")
            append("§eОчередь §f$kitName§e: §f${queue.size} игроков\n")
            append("§6═══════════════════════════\n")
            
            queue.take(5).forEach { entry ->
                val waitTime = (System.currentTimeMillis() - entry.queueTime) / 1000
                append("§7- §f${entry.playerName} §7(§e${entry.rating}§7) - ${waitTime}s\n")
            }
            
            if (queue.size > 5) {
                append("§7... и еще ${queue.size - 5} игроков\n")
            }
            
            append("§6═══════════════════════════")
        }
    }
    
    /**
     * Получить статистику
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "total_in_queue" to queues.values.sumOf { it.size },
            "queues" to queues.mapValues { it.value.size }
        )
    }
    
    /**
     * Очистка при выходе игрока
     */
    fun cleanupPlayer(uuid: UUID) {
        queues.values.forEach { queue ->
            queue.removeIf { it.uuid == uuid }
        }
    }
}
