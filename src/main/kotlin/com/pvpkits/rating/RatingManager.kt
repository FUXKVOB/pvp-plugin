package com.pvpkits.rating

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.CoroutineUtils
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class RatingManager(private val plugin: PvPKitsPlugin) {
    
    private val ratings = ConcurrentHashMap<UUID, EloRating>()
    private lateinit var connection: Connection
    
    /**
     * Initialize database
     */
    suspend fun initialize() {
        CoroutineUtils.io {
            val dbFile = plugin.dataFolder.resolve("ratings.db")
            connection = DriverManager.getConnection("jdbc:sqlite:$dbFile")
            
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS elo_ratings (
                    uuid TEXT PRIMARY KEY,
                    rating INTEGER DEFAULT 1000,
                    wins INTEGER DEFAULT 0,
                    losses INTEGER DEFAULT 0,
                    win_streak INTEGER DEFAULT 0,
                    best_win_streak INTEGER DEFAULT 0,
                    rank TEXT DEFAULT 'UNRANKED',
                    last_updated INTEGER
                )
            """)
            
            plugin.logger.info("ELO Rating system initialized")
        }
    }
    
    /**
     * Load player rating
     */
    suspend fun loadRating(uuid: UUID): EloRating {
        return ratings.getOrPut(uuid) {
            CoroutineUtils.io {
                val stmt = connection.prepareStatement(
                    "SELECT * FROM elo_ratings WHERE uuid = ?"
                )
                stmt.setString(1, uuid.toString())
                val rs = stmt.executeQuery()
                
                if (rs.next()) {
                    EloRating(
                        uuid = uuid,
                        rating = rs.getInt("rating"),
                        wins = rs.getInt("wins"),
                        losses = rs.getInt("losses"),
                        winStreak = rs.getInt("win_streak"),
                        bestWinStreak = rs.getInt("best_win_streak"),
                        rank = EloRank.valueOf(rs.getString("rank")),
                        lastUpdated = rs.getLong("last_updated")
                    )
                } else {
                    EloRating(uuid)
                }
            }
        }
    }
    
    /**
     * Save player rating
     */
    suspend fun saveRating(rating: EloRating) {
        CoroutineUtils.io {
            val stmt = connection.prepareStatement("""
                INSERT OR REPLACE INTO elo_ratings 
                (uuid, rating, wins, losses, win_streak, best_win_streak, rank, last_updated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)
            
            stmt.setString(1, rating.uuid.toString())
            stmt.setInt(2, rating.rating)
            stmt.setInt(3, rating.wins)
            stmt.setInt(4, rating.losses)
            stmt.setInt(5, rating.winStreak)
            stmt.setInt(6, rating.bestWinStreak)
            stmt.setString(7, rating.rank.name)
            stmt.setLong(8, rating.lastUpdated)
            
            stmt.executeUpdate()
        }
    }
    
    /**
     * Record match result and update ratings
     */
    suspend fun recordMatch(winner: UUID, loser: UUID) {
        val winnerRating = loadRating(winner)
        val loserRating = loadRating(loser)
        
        // Calculate new ratings
        val (newWinnerRating, newLoserRating) = EloCalculator.calculateNewRatings(
            winnerRating.rating,
            loserRating.rating,
            winnerRating.getTotalMatches(),
            loserRating.getTotalMatches()
        )
        
        val winnerChange = newWinnerRating - winnerRating.rating
        val loserChange = newLoserRating - loserRating.rating
        
        // Update winner
        winnerRating.rating = newWinnerRating
        winnerRating.wins++
        winnerRating.winStreak++
        if (winnerRating.winStreak > winnerRating.bestWinStreak) {
            winnerRating.bestWinStreak = winnerRating.winStreak
        }
        winnerRating.updateRank()
        winnerRating.lastUpdated = System.currentTimeMillis()
        
        // Update loser
        loserRating.rating = newLoserRating
        loserRating.losses++
        loserRating.winStreak = 0
        loserRating.updateRank()
        loserRating.lastUpdated = System.currentTimeMillis()
        
        // Save both
        saveRating(winnerRating)
        saveRating(loserRating)
        
        // Notify players
        plugin.server.getPlayer(winner)?.let { player ->
            notifyRatingChange(player, winnerChange, winnerRating)
        }
        
        plugin.server.getPlayer(loser)?.let { player ->
            notifyRatingChange(player, loserChange, loserRating)
        }
    }
    
    /**
     * Notify player of rating change
     */
    private fun notifyRatingChange(player: Player, change: Int, rating: EloRating) {
        val changeStr = if (change >= 0) "§a+$change" else "§c$change"
        val rankColor = rating.rank.color
        
        player.sendMessage("")
        player.sendMessage("§6§l═══════════════════════════")
        player.sendMessage("§e  Rating: §f${rating.rating} $changeStr")
        player.sendMessage("§e  Rank: $rankColor${rating.rank.displayName}")
        player.sendMessage("§e  W/L: §f${rating.wins}/${rating.losses}")
        player.sendMessage("§6§l═══════════════════════════")
        player.sendMessage("")
    }
    
    /**
     * Get top players by rating
     */
    suspend fun getTopPlayers(limit: Int = 10): List<EloRating> {
        return CoroutineUtils.io {
            val stmt = connection.prepareStatement(
                "SELECT * FROM elo_ratings ORDER BY rating DESC LIMIT ?"
            )
            stmt.setInt(1, limit)
            val rs = stmt.executeQuery()
            
            val list = mutableListOf<EloRating>()
            while (rs.next()) {
                list.add(EloRating(
                    uuid = UUID.fromString(rs.getString("uuid")),
                    rating = rs.getInt("rating"),
                    wins = rs.getInt("wins"),
                    losses = rs.getInt("losses"),
                    winStreak = rs.getInt("win_streak"),
                    bestWinStreak = rs.getInt("best_win_streak"),
                    rank = EloRank.valueOf(rs.getString("rank")),
                    lastUpdated = rs.getLong("last_updated")
                ))
            }
            list
        }
    }
    
    /**
     * Get player rank position
     */
    suspend fun getPlayerRank(uuid: UUID): Int {
        return CoroutineUtils.io {
            val stmt = connection.prepareStatement(
                "SELECT COUNT(*) + 1 as rank FROM elo_ratings WHERE rating > (SELECT rating FROM elo_ratings WHERE uuid = ?)"
            )
            stmt.setString(1, uuid.toString())
            val rs = stmt.executeQuery()
            
            if (rs.next()) rs.getInt("rank") else 0
        }
    }
    
    /**
     * Get rating from cache
     */
    fun getRating(uuid: UUID): EloRating? = ratings[uuid]
    
    /**
     * Cleanup
     */
    fun shutdown() {
        if (::connection.isInitialized && !connection.isClosed) {
            connection.close()
        }
    }
    
    /**
     * Get memory stats
     */
    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "cached_ratings" to ratings.size
        )
    }
}
