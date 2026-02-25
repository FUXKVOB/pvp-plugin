package com.pvpkits.duel

import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Represents a 1v1 duel match
 */
data class DuelMatch(
    val id: String,
    val player1: UUID,
    val player2: UUID,
    val kitName: String,
    val spawn1: Location,
    val spawn2: Location,
    var currentRound: Int = 1,
    val maxRounds: Int = 3,
    val winsNeeded: Int = 2,
    var player1Wins: Int = 0,
    var player2Wins: Int = 0,
    var state: DuelState = DuelState.WAITING,
    var startTime: Long = System.currentTimeMillis()
) {
    /**
     * Get opponent UUID
     */
    fun getOpponent(uuid: UUID): UUID {
        return if (uuid == player1) player2 else player1
    }
    
    /**
     * Check if player is in this match
     */
    fun isPlayer(uuid: UUID): Boolean {
        return uuid == player1 || uuid == player2
    }
    
    /**
     * Get player wins
     */
    fun getWins(uuid: UUID): Int {
        return if (uuid == player1) player1Wins else player2Wins
    }
    
    /**
     * Add win for player
     */
    fun addWin(uuid: UUID) {
        if (uuid == player1) player1Wins++ else player2Wins++
    }
    
    /**
     * Check if match is over (someone reached wins needed)
     */
    fun isMatchOver(): Boolean {
        return player1Wins >= winsNeeded || player2Wins >= winsNeeded
    }
    
    /**
     * Get winner UUID (null if no winner yet)
     */
    fun getWinner(): UUID? {
        return when {
            player1Wins >= winsNeeded -> player1
            player2Wins >= winsNeeded -> player2
            else -> null
        }
    }
    
    /**
     * Get loser UUID
     */
    fun getLoser(): UUID? {
        return when {
            player1Wins >= winsNeeded -> player2
            player2Wins >= winsNeeded -> player1
            else -> null
        }
    }
    
    /**
     * Get spawn for player
     */
    fun getSpawn(uuid: UUID): Location {
        return if (uuid == player1) spawn1 else spawn2
    }
    
    /**
     * Get score string for display
     */
    fun getScoreString(): String {
        return "§e$player1Wins §7- §e$player2Wins"
    }
}

/**
 * Duel state enum
 */
enum class DuelState {
    WAITING,      // Waiting for players to ready up
    COUNTDOWN,    // 5 second countdown
    IN_PROGRESS,  // Round in progress
    ROUND_END,    // Round just ended
    MATCH_END     // Match is over
}

/**
 * Player queue entry
 */
data class DuelQueueEntry(
    val uuid: UUID,
    val playerName: String,
    val kitName: String,
    val joinTime: Long = System.currentTimeMillis()
)
