package com.pvpkits.replay

import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.*

/**
 * Replay recording data
 */
data class ReplayData(
    val id: String,
    val player1: UUID,
    val player2: UUID,
    val player1Name: String,
    val player2Name: String,
    val kitName: String,
    val winner: UUID,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val frames: List<ReplayFrame> = emptyList()
) {
    fun getDurationSeconds(): Int = (duration / 1000).toInt()
}

/**
 * Single frame in replay
 */
data class ReplayFrame(
    val timestamp: Long,
    val player1Location: Location,
    val player2Location: Location,
    val player1Health: Double,
    val player2Health: Double,
    val player1Yaw: Float,
    val player1Pitch: Float,
    val player2Yaw: Float,
    val player2Pitch: Float,
    val events: List<ReplayEvent> = emptyList()
)

/**
 * Events that happened in a frame
 */
sealed class ReplayEvent {
    data class Hit(val attacker: UUID, val victim: UUID, val damage: Double) : ReplayEvent()
    data class Death(val victim: UUID, val killer: UUID?) : ReplayEvent()
    data class ItemUse(val player: UUID, val item: String) : ReplayEvent()
    data class PotionEffect(val player: UUID, val effect: String) : ReplayEvent()
}

/**
 * Replay recorder
 */
class ReplayRecorder(
    val matchId: String,
    val player1: UUID,
    val player2: UUID,
    val player1Name: String,
    val player2Name: String,
    val kitName: String
) {
    private val frames = mutableListOf<ReplayFrame>()
    private val startTime = System.currentTimeMillis()
    private var endTime: Long = 0
    private var winner: UUID? = null
    
    /**
     * Record a frame
     */
    fun recordFrame(
        p1Location: Location,
        p2Location: Location,
        p1Health: Double,
        p2Health: Double,
        p1Yaw: Float,
        p1Pitch: Float,
        p2Yaw: Float,
        p2Pitch: Float,
        events: List<ReplayEvent> = emptyList()
    ) {
        frames.add(ReplayFrame(
            timestamp = System.currentTimeMillis() - startTime,
            player1Location = p1Location.clone(),
            player2Location = p2Location.clone(),
            player1Health = p1Health,
            player2Health = p2Health,
            player1Yaw = p1Yaw,
            player1Pitch = p1Pitch,
            player2Yaw = p2Yaw,
            player2Pitch = p2Pitch,
            events = events
        ))
    }
    
    /**
     * Finish recording
     */
    fun finish(winnerUUID: UUID): ReplayData {
        endTime = System.currentTimeMillis()
        winner = winnerUUID
        
        return ReplayData(
            id = matchId,
            player1 = player1,
            player2 = player2,
            player1Name = player1Name,
            player2Name = player2Name,
            kitName = kitName,
            winner = winnerUUID,
            startTime = startTime,
            endTime = endTime,
            duration = endTime - startTime,
            frames = frames.toList()
        )
    }
    
    /**
     * Get frame count
     */
    fun getFrameCount(): Int = frames.size
}
