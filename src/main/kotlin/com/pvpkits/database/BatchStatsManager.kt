package com.pvpkits.database

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.CoroutineUtils
import kotlinx.coroutines.launch
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Batch Stats Manager - оптимизированные операции с БД
 * 
 * Группирует UPDATE/INSERT запросы для уменьшения нагрузки на диск
 */
class BatchStatsManager(private val plugin: PvPKitsPlugin) {
    
    private val pendingStatUpdates = ConcurrentLinkedQueue<StatUpdate>()
    private val pendingKitStatUpdates = ConcurrentLinkedQueue<KitStatUpdate>()
    private val pendingMatchHistory = ConcurrentLinkedQueue<MatchHistoryEntry>()
    private val pendingRatingUpdates = ConcurrentLinkedQueue<RatingUpdate>()
    
    data class StatUpdate(
        val uuid: UUID,
        val kills: Int,
        val deaths: Int,
        val timestamp: Long
    )
    
    data class KitStatUpdate(
        val uuid: UUID,
        val kitName: String,
        val kills: Int,
        val deaths: Int,
        val wins: Int,
        val losses: Int,
        val damageDealt: Double,
        val damageTaken: Double
    )
    
    data class MatchHistoryEntry(
        val playerUUID: UUID,
        val opponentUUID: UUID,
        val kitName: String,
        val result: String,
        val duration: Int,
        val kills: Int,
        val deaths: Int,
        val damageDealt: Double,
        val damageTaken: Double,
        val timestamp: Long
    )
    
    data class RatingUpdate(
        val uuid: UUID,
        val rating: Int,
        val wins: Int,
        val losses: Int,
        val winStreak: Int,
        val timestamp: Long
    )
    
    companion object {
        private const val FLUSH_INTERVAL = 600L // 30 секунд
        private const val MAX_BATCH_SIZE = 100
    }
    
    init {
        // Автоматический flush каждые 30 секунд
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            flushAll()
        }, FLUSH_INTERVAL, FLUSH_INTERVAL)
        
        plugin.logger.info("BatchStatsManager initialized - auto-flush every 30s")
    }
    
    /**
     * Добавить обновление статистики в очередь
     */
    fun queueStatUpdate(uuid: UUID, kills: Int, deaths: Int) {
        pendingStatUpdates.offer(StatUpdate(
            uuid = uuid,
            kills = kills,
            deaths = deaths,
            timestamp = System.currentTimeMillis()
        ))
        
        checkFlush()
    }
    
    /**
     * Добавить обновление статистики по киту
     */
    fun queueKitStatUpdate(
        uuid: UUID,
        kitName: String,
        kills: Int,
        deaths: Int,
        wins: Int,
        losses: Int,
        damageDealt: Double,
        damageTaken: Double
    ) {
        pendingKitStatUpdates.offer(KitStatUpdate(
            uuid = uuid,
            kitName = kitName,
            kills = kills,
            deaths = deaths,
            wins = wins,
            losses = losses,
            damageDealt = damageDealt,
            damageTaken = damageTaken
        ))
        
        checkFlush()
    }
    
    /**
     * Добавить запись в историю матчей
     */
    fun queueMatchHistory(
        playerUUID: UUID,
        opponentUUID: UUID,
        kitName: String,
        won: Boolean,
        duration: Int,
        kills: Int,
        deaths: Int,
        damageDealt: Double,
        damageTaken: Double
    ) {
        pendingMatchHistory.offer(MatchHistoryEntry(
            playerUUID = playerUUID,
            opponentUUID = opponentUUID,
            kitName = kitName,
            result = if (won) "WIN" else "LOSS",
            duration = duration,
            kills = kills,
            deaths = deaths,
            damageDealt = damageDealt,
            damageTaken = damageTaken,
            timestamp = System.currentTimeMillis()
        ))
        
        checkFlush()
    }
    
    /**
     * Добавить обновление рейтинга
     */
    fun queueRatingUpdate(
        uuid: UUID,
        rating: Int,
        wins: Int,
        losses: Int,
        winStreak: Int
    ) {
        pendingRatingUpdates.offer(RatingUpdate(
            uuid = uuid,
            rating = rating,
            wins = wins,
            losses = losses,
            winStreak = winStreak,
            timestamp = System.currentTimeMillis()
        ))
        
        checkFlush()
    }
    
    /**
     * Проверить нужен ли flush
     */
    private fun checkFlush() {
        val totalPending = pendingStatUpdates.size + 
                          pendingKitStatUpdates.size + 
                          pendingMatchHistory.size +
                          pendingRatingUpdates.size
        
        if (totalPending >= MAX_BATCH_SIZE) {
            plugin.launch {
                flushAll()
            }
        }
    }
    
    /**
     * Flush всех очередей
     */
    fun flushAll() {
        plugin.launch {
            try {
                flushStatUpdates()
                flushKitStatUpdates()
                flushMatchHistory()
                flushRatingUpdates()
            } catch (e: Exception) {
                plugin.logger.severe("Error flushing batch updates: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Flush обновлений статистики
     */
    private suspend fun flushStatUpdates() {
        if (pendingStatUpdates.isEmpty()) return
        
        val updates = mutableListOf<StatUpdate>()
        while (pendingStatUpdates.isNotEmpty()) {
            pendingStatUpdates.poll()?.let { updates.add(it) }
        }
        
        if (updates.isEmpty()) return
        
        CoroutineUtils.io {
            plugin.statsManager.getConnection().use { conn ->
                conn.autoCommit = false
                
                try {
                    val stmt = conn.prepareStatement("""
                        INSERT INTO stats (uuid, player_name, kills, deaths, last_seen)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET
                            kills = kills + excluded.kills,
                            deaths = deaths + excluded.deaths,
                            last_seen = excluded.last_seen
                    """)
                    
                    updates.forEach { update ->
                        stmt.setString(1, update.uuid.toString())
                        stmt.setString(2, plugin.server.getOfflinePlayer(update.uuid).name ?: "Unknown")
                        stmt.setInt(3, update.kills)
                        stmt.setInt(4, update.deaths)
                        stmt.setLong(5, update.timestamp)
                        stmt.addBatch()
                    }
                    
                    stmt.executeBatch()
                    conn.commit()
                    
                    plugin.logger.info("Flushed ${updates.size} stat updates")
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }
    }
    
    /**
     * Flush обновлений статистики по китам
     */
    private suspend fun flushKitStatUpdates() {
        if (pendingKitStatUpdates.isEmpty()) return
        
        val updates = mutableListOf<KitStatUpdate>()
        while (pendingKitStatUpdates.isNotEmpty()) {
            pendingKitStatUpdates.poll()?.let { updates.add(it) }
        }
        
        if (updates.isEmpty()) return
        
        CoroutineUtils.io {
            plugin.enhancedStatsManager.getConnection().use { conn ->
                conn.autoCommit = false
                
                try {
                    val stmt = conn.prepareStatement("""
                        INSERT INTO kit_stats (uuid, kit_name, kills, deaths, wins, losses, damage_dealt, damage_taken)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(uuid, kit_name) DO UPDATE SET
                            kills = kills + excluded.kills,
                            deaths = deaths + excluded.deaths,
                            wins = wins + excluded.wins,
                            losses = losses + excluded.losses,
                            damage_dealt = damage_dealt + excluded.damage_dealt,
                            damage_taken = damage_taken + excluded.damage_taken
                    """)
                    
                    updates.forEach { update ->
                        stmt.setString(1, update.uuid.toString())
                        stmt.setString(2, update.kitName)
                        stmt.setInt(3, update.kills)
                        stmt.setInt(4, update.deaths)
                        stmt.setInt(5, update.wins)
                        stmt.setInt(6, update.losses)
                        stmt.setDouble(7, update.damageDealt)
                        stmt.setDouble(8, update.damageTaken)
                        stmt.addBatch()
                    }
                    
                    stmt.executeBatch()
                    conn.commit()
                    
                    plugin.logger.info("Flushed ${updates.size} kit stat updates")
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }
    }
    
    /**
     * Flush истории матчей
     */
    private suspend fun flushMatchHistory() {
        if (pendingMatchHistory.isEmpty()) return
        
        val entries = mutableListOf<MatchHistoryEntry>()
        while (pendingMatchHistory.isNotEmpty()) {
            pendingMatchHistory.poll()?.let { entries.add(it) }
        }
        
        if (entries.isEmpty()) return
        
        CoroutineUtils.io {
            plugin.enhancedStatsManager.getConnection().use { conn ->
                conn.autoCommit = false
                
                try {
                    val stmt = conn.prepareStatement("""
                        INSERT INTO match_history 
                        (player_uuid, opponent_uuid, kit_name, result, duration, kills, deaths, damage_dealt, damage_taken, timestamp)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    
                    entries.forEach { entry ->
                        stmt.setString(1, entry.playerUUID.toString())
                        stmt.setString(2, entry.opponentUUID.toString())
                        stmt.setString(3, entry.kitName)
                        stmt.setString(4, entry.result)
                        stmt.setInt(5, entry.duration)
                        stmt.setInt(6, entry.kills)
                        stmt.setInt(7, entry.deaths)
                        stmt.setDouble(8, entry.damageDealt)
                        stmt.setDouble(9, entry.damageTaken)
                        stmt.setLong(10, entry.timestamp)
                        stmt.addBatch()
                    }
                    
                    stmt.executeBatch()
                    conn.commit()
                    
                    plugin.logger.info("Flushed ${entries.size} match history entries")
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }
    }
    
    /**
     * Flush обновлений рейтинга
     */
    private suspend fun flushRatingUpdates() {
        if (pendingRatingUpdates.isEmpty()) return
        
        val updates = mutableListOf<RatingUpdate>()
        while (pendingRatingUpdates.isNotEmpty()) {
            pendingRatingUpdates.poll()?.let { updates.add(it) }
        }
        
        if (updates.isEmpty()) return
        
        CoroutineUtils.io {
            plugin.ratingManager.getConnection().use { conn ->
                conn.autoCommit = false
                
                try {
                    val stmt = conn.prepareStatement("""
                        INSERT INTO elo_ratings (uuid, rating, wins, losses, win_streak, last_updated)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET
                            rating = excluded.rating,
                            wins = excluded.wins,
                            losses = excluded.losses,
                            win_streak = excluded.win_streak,
                            last_updated = excluded.last_updated
                    """)
                    
                    updates.forEach { update ->
                        stmt.setString(1, update.uuid.toString())
                        stmt.setInt(2, update.rating)
                        stmt.setInt(3, update.wins)
                        stmt.setInt(4, update.losses)
                        stmt.setInt(5, update.winStreak)
                        stmt.setLong(6, update.timestamp)
                        stmt.addBatch()
                    }
                    
                    stmt.executeBatch()
                    conn.commit()
                    
                    plugin.logger.info("Flushed ${updates.size} rating updates")
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }
    }
    
    /**
     * Получить статистику очередей
     */
    fun getQueueStats(): Map<String, Int> {
        return mapOf(
            "pending_stats" to pendingStatUpdates.size,
            "pending_kit_stats" to pendingKitStatUpdates.size,
            "pending_match_history" to pendingMatchHistory.size,
            "pending_ratings" to pendingRatingUpdates.size,
            "total_pending" to (pendingStatUpdates.size + pendingKitStatUpdates.size + 
                               pendingMatchHistory.size + pendingRatingUpdates.size)
        )
    }
    
    /**
     * Shutdown - flush всех данных
     */
    fun shutdown() {
        plugin.logger.info("Shutting down BatchStatsManager - flushing all pending updates...")
        flushAll()
        plugin.logger.info("BatchStatsManager shutdown complete")
    }
}
