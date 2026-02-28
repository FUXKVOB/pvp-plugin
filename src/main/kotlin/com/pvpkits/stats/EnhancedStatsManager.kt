package com.pvpkits.stats

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.CoroutineUtils
import kotlinx.coroutines.launch
import java.sql.Connection
import java.util.*

/**
 * Enhanced statistics with detailed tracking
 */
class EnhancedStatsManager(private val plugin: PvPKitsPlugin) {
    
    private lateinit var connection: Connection
    
    /**
     * Initialize enhanced stats tables
     */
    suspend fun initialize(conn: Connection) {
        connection = conn
        
        CoroutineUtils.io {
            // Kit-specific stats
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS kit_stats (
                    uuid TEXT,
                    kit_name TEXT,
                    kills INTEGER DEFAULT 0,
                    deaths INTEGER DEFAULT 0,
                    wins INTEGER DEFAULT 0,
                    losses INTEGER DEFAULT 0,
                    damage_dealt REAL DEFAULT 0,
                    damage_taken REAL DEFAULT 0,
                    PRIMARY KEY (uuid, kit_name)
                )
            """)
            
            // Match history
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS match_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT,
                    opponent_uuid TEXT,
                    kit_name TEXT,
                    result TEXT,
                    duration INTEGER,
                    kills INTEGER,
                    deaths INTEGER,
                    damage_dealt REAL,
                    damage_taken REAL,
                    timestamp INTEGER
                )
            """)
            
            // Daily stats
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS daily_stats (
                    uuid TEXT,
                    date TEXT,
                    kills INTEGER DEFAULT 0,
                    deaths INTEGER DEFAULT 0,
                    wins INTEGER DEFAULT 0,
                    losses INTEGER DEFAULT 0,
                    playtime INTEGER DEFAULT 0,
                    PRIMARY KEY (uuid, date)
                )
            """)
            
            // Achievements
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS achievements (
                    uuid TEXT,
                    achievement_id TEXT,
                    unlocked_at INTEGER,
                    PRIMARY KEY (uuid, achievement_id)
                )
            """)
            
            plugin.logger.info("Enhanced stats system initialized")
        }
    }
    
    /**
     * Record kit-specific stats
     */
    suspend fun recordKitStats(
        uuid: UUID,
        kitName: String,
        kills: Int = 0,
        deaths: Int = 0,
        wins: Int = 0,
        losses: Int = 0,
        damageDealt: Double = 0.0,
        damageTaken: Double = 0.0
    ) {
        CoroutineUtils.io {
            val stmt = connection.prepareStatement("""
                INSERT INTO kit_stats (uuid, kit_name, kills, deaths, wins, losses, damage_dealt, damage_taken)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, kit_name) DO UPDATE SET
                    kills = kills + ?,
                    deaths = deaths + ?,
                    wins = wins + ?,
                    losses = losses + ?,
                    damage_dealt = damage_dealt + ?,
                    damage_taken = damage_taken + ?
            """)
            
            stmt.setString(1, uuid.toString())
            stmt.setString(2, kitName)
            stmt.setInt(3, kills)
            stmt.setInt(4, deaths)
            stmt.setInt(5, wins)
            stmt.setInt(6, losses)
            stmt.setDouble(7, damageDealt)
            stmt.setDouble(8, damageTaken)
            stmt.setInt(9, kills)
            stmt.setInt(10, deaths)
            stmt.setInt(11, wins)
            stmt.setInt(12, losses)
            stmt.setDouble(13, damageDealt)
            stmt.setDouble(14, damageTaken)
            
            stmt.executeUpdate()
        }
    }
    
    /**
     * Record match in history
     */
    suspend fun recordMatch(
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
        CoroutineUtils.io {
            val stmt = connection.prepareStatement("""
                INSERT INTO match_history 
                (player_uuid, opponent_uuid, kit_name, result, duration, kills, deaths, damage_dealt, damage_taken, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
            
            stmt.setString(1, playerUUID.toString())
            stmt.setString(2, opponentUUID.toString())
            stmt.setString(3, kitName)
            stmt.setString(4, if (won) "WIN" else "LOSS")
            stmt.setInt(5, duration)
            stmt.setInt(6, kills)
            stmt.setInt(7, deaths)
            stmt.setDouble(8, damageDealt)
            stmt.setDouble(9, damageTaken)
            stmt.setLong(10, System.currentTimeMillis())
            
            stmt.executeUpdate()
        }
    }
    
    /**
     * Get kit stats for player
     */
    suspend fun getKitStats(uuid: UUID, kitName: String): KitStats? {
        return CoroutineUtils.io {
            val stmt = connection.prepareStatement(
                "SELECT * FROM kit_stats WHERE uuid = ? AND kit_name = ?"
            )
            stmt.setString(1, uuid.toString())
            stmt.setString(2, kitName)
            val rs = stmt.executeQuery()
            
            if (rs.next()) {
                KitStats(
                    uuid = uuid,
                    kitName = kitName,
                    kills = rs.getInt("kills"),
                    deaths = rs.getInt("deaths"),
                    wins = rs.getInt("wins"),
                    losses = rs.getInt("losses"),
                    damageDealt = rs.getDouble("damage_dealt"),
                    damageTaken = rs.getDouble("damage_taken")
                )
            } else null
        }
    }
    
    /**
     * Get recent matches
     */
    suspend fun getRecentMatches(uuid: UUID, limit: Int = 10): List<MatchRecord> {
        return CoroutineUtils.io {
            val stmt = connection.prepareStatement(
                "SELECT * FROM match_history WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?"
            )
            stmt.setString(1, uuid.toString())
            stmt.setInt(2, limit)
            val rs = stmt.executeQuery()
            
            val matches = mutableListOf<MatchRecord>()
            while (rs.next()) {
                matches.add(MatchRecord(
                    playerUUID = uuid,
                    opponentUUID = UUID.fromString(rs.getString("opponent_uuid")),
                    kitName = rs.getString("kit_name"),
                    result = rs.getString("result"),
                    duration = rs.getInt("duration"),
                    kills = rs.getInt("kills"),
                    deaths = rs.getInt("deaths"),
                    damageDealt = rs.getDouble("damage_dealt"),
                    damageTaken = rs.getDouble("damage_taken"),
                    timestamp = rs.getLong("timestamp")
                ))
            }
            matches
        }
    }
    
    /**
     * Unlock achievement
     */
    suspend fun unlockAchievement(uuid: UUID, achievementId: String) {
        CoroutineUtils.io {
            val stmt = connection.prepareStatement(
                "INSERT OR IGNORE INTO achievements (uuid, achievement_id, unlocked_at) VALUES (?, ?, ?)"
            )
            stmt.setString(1, uuid.toString())
            stmt.setString(2, achievementId)
            stmt.setLong(3, System.currentTimeMillis())
            stmt.executeUpdate()
            
            // Notify player
            plugin.server.getPlayer(uuid)?.let { player ->
                val achievement = Achievement.fromId(achievementId)
                player.sendMessage("§6§l✦ Achievement Unlocked! §r§e${achievement?.displayName}")
            }
        }
    }
}

/**
 * Kit-specific stats
 */
data class KitStats(
    val uuid: UUID,
    val kitName: String,
    val kills: Int,
    val deaths: Int,
    val wins: Int,
    val losses: Int,
    val damageDealt: Double,
    val damageTaken: Double
) {
    fun getKD(): Double = if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()
    fun getWinRate(): Double = if (wins + losses > 0) (wins.toDouble() / (wins + losses)) * 100 else 0.0
}

/**
 * Match record
 */
data class MatchRecord(
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

/**
 * Achievements
 */
enum class Achievement(val id: String, val displayName: String, val description: String) {
    FIRST_BLOOD("first_blood", "First Blood", "Get your first kill"),
    KILLING_SPREE("killing_spree", "Killing Spree", "Get a 5 kill streak"),
    UNSTOPPABLE("unstoppable", "Unstoppable", "Get a 10 kill streak"),
    GODLIKE("godlike", "Godlike", "Get a 20 kill streak"),
    CENTURION("centurion", "Centurion", "Get 100 total kills"),
    GLADIATOR("gladiator", "Gladiator", "Get 500 total kills"),
    LEGEND("legend", "Legend", "Get 1000 total kills"),
    TOURNAMENT_WINNER("tournament_winner", "Tournament Winner", "Win a tournament"),
    PERFECT_MATCH("perfect_match", "Perfect Match", "Win a match without dying"),
    KIT_MASTER("kit_master", "Kit Master", "Get 50 kills with one kit");
    
    companion object {
        fun fromId(id: String): Achievement? = values().find { it.id == id }
    }
}
