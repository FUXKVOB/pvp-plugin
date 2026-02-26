package com.pvpkits.stats

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.shynixn.mccoroutine.bukkit.launch
import com.pvpkits.PvPKitsPlugin
import com.pvpkits.database.DatabaseManager
import com.pvpkits.utils.ComponentCache
import com.pvpkits.utils.CoroutineUtils
import com.pvpkits.utils.SchedulerUtils
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages player statistics with SQLite database backend and advanced caching
 * 
 * Best Practices 2026:
 * - Caffeine cache for frequently accessed stats
 * - Batch database operations
 * - Async I/O operations
 * - Memory-efficient data structures
 * - Proper cleanup to prevent memory leaks
 */
class StatsManager(private val plugin: PvPKitsPlugin) {
    
    // Primary stats storage (always in memory for fast access)
    private val stats = ConcurrentHashMap<UUID, PlayerStats>()
    
    // Caffeine cache for leaderboard queries (expensive DB operations)
    private val leaderboardCache: Cache<String, List<PlayerStats>> = Caffeine.newBuilder()
        .maximumSize(10)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .recordStats()
        .build()
    
    private val dbManager = DatabaseManager(plugin)
    private var needsSave = false
    
    companion object {
        private const val AUTOSAVE_INTERVAL = 5 * 60 * 20L // 5 minutes in ticks
    }
    
    init {
        dbManager.initialize()
    }
    
    /**
     * Load all stats from database
     */
    suspend fun loadStats() {
        CoroutineUtils.io {
            dbManager.executeSync { connection ->
                val statement = connection.prepareStatement(
                    "SELECT * FROM player_stats"
                )
                val resultSet = statement.executeQuery()
                
                while (resultSet.next()) {
                    try {
                        val uuid = UUID.fromString(resultSet.getString("uuid"))
                        val playerStats = PlayerStats(
                            uuid = uuid,
                            playerName = resultSet.getString("player_name"),
                            kills = resultSet.getInt("kills"),
                            deaths = resultSet.getInt("deaths"),
                            currentKillstreak = resultSet.getInt("current_killstreak"),
                            bestKillstreak = resultSet.getInt("best_killstreak"),
                            lastKitUsed = resultSet.getString("last_kit_used"),
                            lastUpdated = resultSet.getLong("last_updated")
                        )
                        
                        // Load kit usage
                        loadKitUsage(connection, uuid, playerStats)
                        
                        stats[uuid] = playerStats
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to load stats: ${e.message}")
                    }
                }
                
                resultSet.close()
                statement.close()
            }
            
            plugin.logger.info("Loaded ${stats.size} player stats from database")
        }
    }
    
    /**
     * Load kit usage for a player
     */
    private fun loadKitUsage(connection: java.sql.Connection, uuid: UUID, playerStats: PlayerStats) {
        val statement = connection.prepareStatement(
            "SELECT kit_name, use_count FROM kit_usage WHERE player_uuid = ?"
        )
        statement.setString(1, uuid.toString())
        val resultSet = statement.executeQuery()
        
        while (resultSet.next()) {
            val kitName = resultSet.getString("kit_name")
            val useCount = resultSet.getInt("use_count")
            playerStats.kitsUsed[kitName] = useCount
        }
        
        resultSet.close()
        statement.close()
    }
    
    /**
     * Save all stats to database
     */
    suspend fun saveStats() {
        if (stats.isEmpty()) return
        
        CoroutineUtils.io {
            dbManager.executeBatch { connection ->
                // Prepare statements
                val upsertStats = connection.prepareStatement("""
                    INSERT INTO player_stats (uuid, player_name, kills, deaths, current_killstreak, best_killstreak, last_kit_used, last_updated)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                    player_name = excluded.player_name,
                    kills = excluded.kills,
                    deaths = excluded.deaths,
                    current_killstreak = excluded.current_killstreak,
                    best_killstreak = excluded.best_killstreak,
                    last_kit_used = excluded.last_kit_used,
                    last_updated = excluded.last_updated
                """.trimIndent())
                
                val upsertKitUsage = connection.prepareStatement("""
                    INSERT INTO kit_usage (player_uuid, kit_name, use_count)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid, kit_name) DO UPDATE SET
                    use_count = excluded.use_count
                """.trimIndent())
                
                for ((uuid, playerStats) in stats) {
                    // Save player stats
                    upsertStats.setString(1, uuid.toString())
                    upsertStats.setString(2, playerStats.playerName)
                    upsertStats.setInt(3, playerStats.kills)
                    upsertStats.setInt(4, playerStats.deaths)
                    upsertStats.setInt(5, playerStats.currentKillstreak)
                    upsertStats.setInt(6, playerStats.bestKillstreak)
                    upsertStats.setString(7, playerStats.lastKitUsed)
                    upsertStats.setLong(8, System.currentTimeMillis())
                    upsertStats.addBatch()
                    
                    // Save kit usage
                    for ((kitName, useCount) in playerStats.kitsUsed) {
                        upsertKitUsage.setString(1, uuid.toString())
                        upsertKitUsage.setString(2, kitName)
                        upsertKitUsage.setInt(3, useCount)
                        upsertKitUsage.addBatch()
                    }
                }
                
                upsertStats.executeBatch()
                upsertKitUsage.executeBatch()
                upsertStats.close()
                upsertKitUsage.close()
                
                needsSave = false
            }
            
            plugin.logger.info("Saved ${stats.size} player stats to database")
        }
    }
    
    /**
     * Start autosave task (Folia-compatible)
     */
    fun startAutosave() {
        SchedulerUtils.runTaskTimer(plugin, AUTOSAVE_INTERVAL, AUTOSAVE_INTERVAL, Runnable {
            if (needsSave) {
                plugin.launch {
                    saveStats()
                }
            }
        })
    }
    
    /**
     * Get or create stats for a player
     */
    fun getStats(uuid: UUID, playerName: String): PlayerStats {
        return stats.computeIfAbsent(uuid) { 
            PlayerStats(uuid, playerName)
        }
    }
    
    /**
     * Get stats if exists
     */
    fun getStatsIfExists(uuid: UUID): PlayerStats? = stats[uuid]
    
    /**
     * Record a kill and invalidate leaderboard cache
     */
    fun recordKill(killer: UUID, killerName: String, victim: UUID, victimName: String) {
        val killerStats = getStats(killer, killerName)
        killerStats.addKill()
        
        val victimStats = getStats(victim, victimName)
        victimStats.addDeath()
        
        needsSave = true
        
        // Invalidate leaderboard cache since stats changed
        leaderboardCache.invalidateAll()
        
        // Broadcast killstreak milestones using cached components
        when (killerStats.currentKillstreak) {
            5 -> broadcastMessage("killstreak.5", killerName)
            10 -> broadcastMessage("killstreak.10", killerName)
            15 -> broadcastMessage("killstreak.15", killerName)
            20 -> broadcastMessage("killstreak.20", killerName)
        }
    }
    
    /**
     * Record a death (without killer - environmental) and invalidate cache
     */
    fun recordDeath(uuid: UUID, playerName: String) {
        val playerStats = getStats(uuid, playerName)
        playerStats.addDeath()
        needsSave = true
        leaderboardCache.invalidateAll()
    }
    
    /**
     * Record kit usage
     */
    fun recordKitUse(uuid: UUID, playerName: String, kitName: String) {
        val playerStats = getStats(uuid, playerName)
        playerStats.useKit(kitName)
        needsSave = true
    }
    
    /**
     * Get leaderboard sorted by kills (cached for performance)
     */
    fun getLeaderboard(limit: Int = 10): List<PlayerStats> {
        return leaderboardCache.get("kills:$limit") {
            // Use database for consistent ordering
            dbManager.executeSync { connection ->
                val statement = connection.prepareStatement(
                    "SELECT * FROM player_stats ORDER BY kills DESC LIMIT ?"
                )
                statement.setInt(1, limit)
                val resultSet = statement.executeQuery()
                
                val leaderboard = mutableListOf<PlayerStats>()
                while (resultSet.next()) {
                    val uuid = UUID.fromString(resultSet.getString("uuid"))
                    // Use cached stats if available, otherwise create from DB
                    val stats = this.stats[uuid] ?: PlayerStats(
                        uuid = uuid,
                        playerName = resultSet.getString("player_name"),
                        kills = resultSet.getInt("kills"),
                        deaths = resultSet.getInt("deaths"),
                        currentKillstreak = resultSet.getInt("current_killstreak"),
                        bestKillstreak = resultSet.getInt("best_killstreak")
                    )
                    leaderboard.add(stats)
                }
                
                resultSet.close()
                statement.close()
                leaderboard
            } ?: stats.values.sortedByDescending { it.kills }.take(limit)
        }!!
    }
    
    /**
     * Get leaderboard sorted by K/D ratio (cached)
     */
    fun getLeaderboardByKd(limit: Int = 10): List<PlayerStats> {
        return leaderboardCache.get("kd:$limit") {
            stats.values
                .filter { it.totalGames >= 10 }
                .sortedByDescending { it.kdRatio }
                .take(limit)
        }!!
    }
    
    /**
     * Get leaderboard sorted by killstreak (cached)
     */
    fun getLeaderboardByKillstreak(limit: Int = 10): List<PlayerStats> {
        return leaderboardCache.get("streak:$limit") {
            dbManager.executeSync { connection ->
                val statement = connection.prepareStatement(
                    "SELECT * FROM player_stats ORDER BY best_killstreak DESC LIMIT ?"
                )
                statement.setInt(1, limit)
                val resultSet = statement.executeQuery()
                
                val leaderboard = mutableListOf<PlayerStats>()
                while (resultSet.next()) {
                    val uuid = UUID.fromString(resultSet.getString("uuid"))
                    val stats = this.stats[uuid] ?: PlayerStats(
                        uuid = uuid,
                        playerName = resultSet.getString("player_name"),
                        kills = resultSet.getInt("kills"),
                        deaths = resultSet.getInt("deaths"),
                        bestKillstreak = resultSet.getInt("best_killstreak")
                    )
                    leaderboard.add(stats)
                }
                
                resultSet.close()
                statement.close()
                leaderboard
            } ?: stats.values.sortedByDescending { it.bestKillstreak }.take(limit)
        }!!
    }
    
    /**
     * Get player rank by kills (from database)
     */
    fun getPlayerRank(uuid: UUID): Int {
        return dbManager.executeSync { connection ->
            val statement = connection.prepareStatement(
                "SELECT COUNT(*) + 1 as rank FROM player_stats WHERE kills > (SELECT kills FROM player_stats WHERE uuid = ?)"
            )
            statement.setString(1, uuid.toString())
            val resultSet = statement.executeQuery()
            
            val rank = if (resultSet.next()) resultSet.getInt("rank") else 0
            resultSet.close()
            statement.close()
            rank
        } ?: run {
            val sorted = stats.values.sortedByDescending { it.kills }
            sorted.indexOfFirst { it.uuid == uuid } + 1
        }
    }
    
    /**
     * Get total stats count
     */
    fun getTotalPlayers(): Int = stats.size
    
    /**
     * Clean up player data
     */
    fun cleanupPlayer(uuid: UUID) {
        // Stats are saved, no need to remove from memory
        // They will be reloaded on next join
    }
    
    /**
     * Get memory statistics including cache performance
     */
    fun getMemoryStats(): Map<String, Any> {
        val dbStats = dbManager.getPoolStats()
        val cacheStats = leaderboardCache.stats()
        
        return mapOf(
            "total_players" to stats.size,
            "total_kills" to stats.values.sumOf { it.kills },
            "total_deaths" to stats.values.sumOf { it.deaths },
            "db_pool_active" to (dbStats["active_connections"] ?: 0),
            "db_pool_idle" to (dbStats["idle_connections"] ?: 0),
            "leaderboard_cache_size" to leaderboardCache.estimatedSize(),
            "leaderboard_cache_hit_rate" to cacheStats.hitRate(),
            "leaderboard_cache_miss_rate" to cacheStats.missRate()
        )
    }
    
    /**
     * Shutdown database connection
     */
    fun shutdown() {
        dbManager.shutdown()
    }
    
    private fun broadcastMessage(key: String, vararg args: String) {
        val message = plugin.config.getString("messages.$key") ?: return
        
        // Use ComponentCache for better performance
        val component = try {
            ComponentCache.parseDynamic(
                message,
                Placeholder.unparsed("0", args.getOrNull(0) ?: ""),
                Placeholder.unparsed("1", args.getOrNull(1) ?: "")
            )
        } catch (e: Exception) {
            // Fallback to legacy format
            var formatted = message
            args.forEachIndexed { index, arg ->
                formatted = formatted.replace("{${index}}", arg)
            }
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(formatted.replace("&", "§"))
        }
        
        Bukkit.getOnlinePlayers().forEach { player ->
            player.sendMessage(component)
        }
    }
}
