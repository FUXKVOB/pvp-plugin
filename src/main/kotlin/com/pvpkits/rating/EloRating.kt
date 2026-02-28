package com.pvpkits.rating

import java.util.*

/**
 * ELO Rating data class
 */
data class EloRating(
    val uuid: UUID,
    var rating: Int = 1000,
    var wins: Int = 0,
    var losses: Int = 0,
    var winStreak: Int = 0,
    var bestWinStreak: Int = 0,
    var rank: EloRank = EloRank.UNRANKED,
    var lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Update rank based on rating
     */
    fun updateRank() {
        rank = EloRank.fromRating(rating)
    }
    
    /**
     * Get win rate percentage
     */
    fun getWinRate(): Double {
        val total = wins + losses
        return if (total > 0) (wins.toDouble() / total) * 100 else 0.0
    }
    
    /**
     * Get total matches
     */
    fun getTotalMatches(): Int = wins + losses
}

/**
 * ELO Rank tiers
 */
enum class EloRank(val displayName: String, val minRating: Int, val color: String) {
    UNRANKED("Unranked", 0, "§7"),
    BRONZE("Bronze", 800, "§6"),
    SILVER("Silver", 1000, "§f"),
    GOLD("Gold", 1200, "§e"),
    PLATINUM("Platinum", 1400, "§b"),
    DIAMOND("Diamond", 1600, "§3"),
    MASTER("Master", 1800, "§5"),
    GRANDMASTER("Grandmaster", 2000, "§d"),
    LEGEND("Legend", 2200, "§c");
    
    companion object {
        fun fromRating(rating: Int): EloRank {
            return values()
                .filter { rating >= it.minRating }
                .maxByOrNull { it.minRating } ?: UNRANKED
        }
    }
    
    fun getColoredName(): String = "$color$displayName"
}

/**
 * ELO Calculator using standard formula
 */
object EloCalculator {
    private const val K_FACTOR = 32 // Standard K-factor
    private const val K_FACTOR_NEW = 40 // Higher K for new players (< 30 matches)
    
    /**
     * Calculate expected score
     */
    private fun getExpectedScore(ratingA: Int, ratingB: Int): Double {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0))
    }
    
    /**
     * Calculate new ratings after a match
     * @return Pair of (winner new rating, loser new rating)
     */
    fun calculateNewRatings(
        winnerRating: Int,
        loserRating: Int,
        winnerMatches: Int,
        loserMatches: Int
    ): Pair<Int, Int> {
        val expectedWinner = getExpectedScore(winnerRating, loserRating)
        val expectedLoser = getExpectedScore(loserRating, winnerRating)
        
        // Use higher K-factor for new players
        val kWinner = if (winnerMatches < 30) K_FACTOR_NEW else K_FACTOR
        val kLoser = if (loserMatches < 30) K_FACTOR_NEW else K_FACTOR
        
        // Winner gets 1, loser gets 0
        val newWinnerRating = (winnerRating + kWinner * (1 - expectedWinner)).toInt()
        val newLoserRating = (loserRating + kLoser * (0 - expectedLoser)).toInt()
        
        // Prevent rating from going below 0
        return Pair(
            newWinnerRating.coerceAtLeast(0),
            newLoserRating.coerceAtLeast(0)
        )
    }
    
    /**
     * Calculate rating change
     */
    fun calculateRatingChange(
        playerRating: Int,
        opponentRating: Int,
        playerWon: Boolean,
        playerMatches: Int
    ): Int {
        val expected = getExpectedScore(playerRating, opponentRating)
        val k = if (playerMatches < 30) K_FACTOR_NEW else K_FACTOR
        val actual = if (playerWon) 1.0 else 0.0
        
        return (k * (actual - expected)).toInt()
    }
}
