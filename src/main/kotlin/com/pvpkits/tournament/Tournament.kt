package com.pvpkits.tournament

import java.util.*

data class Tournament(
    val id: String,
    val name: String,
    val kitName: String,
    val maxPlayers: Int,
    val bracketType: BracketType,
    val status: TournamentStatus,
    val participants: MutableSet<UUID> = mutableSetOf(),
    val matches: MutableList<TournamentMatch> = mutableListOf(),
    val winners: MutableList<UUID> = mutableListOf(),
    val prizes: Map<Int, List<String>> = emptyMap(), // place -> commands
    val createdAt: Long = System.currentTimeMillis(),
    var startedAt: Long? = null,
    var finishedAt: Long? = null
)

enum class BracketType {
    SINGLE_ELIMINATION,
    DOUBLE_ELIMINATION
}

enum class TournamentStatus {
    WAITING,      // Waiting for players
    STARTING,     // Countdown before start
    IN_PROGRESS,  // Tournament running
    FINISHED      // Tournament completed
}

data class TournamentMatch(
    val matchId: String,
    val round: Int,
    val player1: UUID,
    val player2: UUID,
    var winner: UUID? = null,
    var status: MatchStatus = MatchStatus.PENDING,
    val startedAt: Long? = null,
    var finishedAt: Long? = null
)

enum class MatchStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}
