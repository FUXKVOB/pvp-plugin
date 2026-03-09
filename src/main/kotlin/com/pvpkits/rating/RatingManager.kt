package com.pvpkits.rating

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.database.DatabaseManager
import com.pvpkits.utils.CoroutineUtils
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RatingManager(
    private val plugin: PvPKitsPlugin,
    private val dbManager: DatabaseManager
) {

    private val ratings = ConcurrentHashMap<UUID, EloRating>()

    suspend fun initialize() {
        CoroutineUtils.io {
            plugin.logger.info("ELO Rating system initialized")
        }
    }

    suspend fun loadRating(uuid: UUID): EloRating {
        ratings[uuid]?.let { return it }

        return CoroutineUtils.io {
            val loaded = dbManager.executeSync { connection ->
                connection.prepareStatement(
                    "SELECT * FROM elo_ratings WHERE uuid = ?"
                ).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.executeQuery().use { rs ->
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
            } ?: EloRating(uuid)

            ratings.putIfAbsent(uuid, loaded) ?: loaded
        }
    }

    suspend fun saveRating(rating: EloRating) {
        CoroutineUtils.io {
            dbManager.executeSync { connection ->
                connection.prepareStatement("""
                    INSERT OR REPLACE INTO elo_ratings
                    (uuid, rating, wins, losses, win_streak, best_win_streak, rank, last_updated)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """).use { stmt ->
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
        }
    }

    suspend fun recordMatch(winner: UUID, loser: UUID) {
        val winnerRating = loadRating(winner)
        val loserRating = loadRating(loser)

        val (newWinnerRating, newLoserRating) = EloCalculator.calculateNewRatings(
            winnerRating.rating,
            loserRating.rating,
            winnerRating.getTotalMatches(),
            loserRating.getTotalMatches()
        )

        val winnerChange = newWinnerRating - winnerRating.rating
        val loserChange = newLoserRating - loserRating.rating

        winnerRating.rating = newWinnerRating
        winnerRating.wins++
        winnerRating.winStreak++
        if (winnerRating.winStreak > winnerRating.bestWinStreak) {
            winnerRating.bestWinStreak = winnerRating.winStreak
        }
        winnerRating.updateRank()
        winnerRating.lastUpdated = System.currentTimeMillis()

        loserRating.rating = newLoserRating
        loserRating.losses++
        loserRating.winStreak = 0
        loserRating.updateRank()
        loserRating.lastUpdated = System.currentTimeMillis()

        saveRating(winnerRating)
        saveRating(loserRating)

        plugin.server.getPlayer(winner)?.let { player ->
            notifyRatingChange(player, winnerChange, winnerRating)
        }

        plugin.server.getPlayer(loser)?.let { player ->
            notifyRatingChange(player, loserChange, loserRating)
        }
    }

    private fun notifyRatingChange(player: Player, change: Int, rating: EloRating) {
        val changeStr = if (change >= 0) "В§a+$change" else "В§c$change"
        val rankColor = rating.rank.color

        player.sendMessage("")
        player.sendMessage("В§6В§lв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђ")
        player.sendMessage("В§e  Rating: В§f${rating.rating} $changeStr")
        player.sendMessage("В§e  Rank: $rankColor${rating.rank.displayName}")
        player.sendMessage("В§e  W/L: В§f${rating.wins}/${rating.losses}")
        player.sendMessage("В§6В§lв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђв•ђ")
        player.sendMessage("")
    }

    suspend fun getTopPlayers(limit: Int = 10): List<EloRating> {
        return CoroutineUtils.io {
            dbManager.executeSync { connection ->
                connection.prepareStatement(
                    "SELECT * FROM elo_ratings ORDER BY rating DESC LIMIT ?"
                ).use { stmt ->
                    stmt.setInt(1, limit)
                    stmt.executeQuery().use { rs ->
                        val list = mutableListOf<EloRating>()
                        while (rs.next()) {
                            list.add(
                                EloRating(
                                    uuid = UUID.fromString(rs.getString("uuid")),
                                    rating = rs.getInt("rating"),
                                    wins = rs.getInt("wins"),
                                    losses = rs.getInt("losses"),
                                    winStreak = rs.getInt("win_streak"),
                                    bestWinStreak = rs.getInt("best_win_streak"),
                                    rank = EloRank.valueOf(rs.getString("rank")),
                                    lastUpdated = rs.getLong("last_updated")
                                )
                            )
                        }
                        list
                    }
                }
            } ?: emptyList()
        }
    }

    suspend fun getPlayerRank(uuid: UUID): Int {
        return CoroutineUtils.io {
            dbManager.executeSync { connection ->
                connection.prepareStatement(
                    "SELECT COUNT(*) + 1 as rank FROM elo_ratings WHERE rating > (SELECT rating FROM elo_ratings WHERE uuid = ?)"
                ).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt("rank") else 0
                    }
                }
            } ?: 0
        }
    }

    fun getRating(uuid: UUID): EloRating? = ratings[uuid]

    fun shutdown() {
        ratings.clear()
    }

    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "cached_ratings" to ratings.size
        )
    }
}
