package com.pvpkits.tournament

import com.github.shynixn.mccoroutine.bukkit.launch
import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import kotlinx.coroutines.delay
import org.bukkit.entity.Player
import java.util.*

class TournamentManager(private val plugin: PvPKitsPlugin) {
    
    private val tournaments = mutableMapOf<String, Tournament>()
    private val playerTournaments = mutableMapOf<UUID, String>() // player -> tournament ID
    
    /**
     * Create a new tournament
     */
    fun createTournament(
        name: String,
        kitName: String,
        maxPlayers: Int,
        bracketType: BracketType,
        prizes: Map<Int, List<String>> = emptyMap()
    ): Tournament {
        val id = UUID.randomUUID().toString().substring(0, 8)
        val tournament = Tournament(
            id = id,
            name = name,
            kitName = kitName,
            maxPlayers = maxPlayers,
            bracketType = bracketType,
            status = TournamentStatus.WAITING,
            prizes = prizes
        )
        
        tournaments[id] = tournament
        plugin.logger.info("Created tournament: $name (ID: $id)")
        
        // Announce tournament
        broadcastToAll("<gradient:#ffd700:#ffaa00><bold>⚔ TOURNAMENT CREATED</bold></gradient>")
        broadcastToAll("<yellow>$name</yellow> <gray>- Kit: <white>$kitName")
        broadcastToAll("<gray>Players: <yellow>0/$maxPlayers</yellow> | Type: <white>${bracketType.name}")
        broadcastToAll("<green>Join with: <yellow>/tournament join $id")
        
        return tournament
    }
    
    /**
     * Join tournament
     */
    fun joinTournament(player: Player, tournamentId: String): Boolean {
        val tournament = tournaments[tournamentId] ?: return false
        
        if (tournament.status != TournamentStatus.WAITING) {
            player.sendMessage(TextUtils.format("<red>This tournament has already started!"))
            return false
        }
        
        if (tournament.participants.size >= tournament.maxPlayers) {
            player.sendMessage(TextUtils.format("<red>Tournament is full!"))
            return false
        }
        
        if (playerTournaments.containsKey(player.uniqueId)) {
            player.sendMessage(TextUtils.format("<red>You are already in a tournament!"))
            return false
        }
        
        tournament.participants.add(player.uniqueId)
        playerTournaments[player.uniqueId] = tournamentId
        
        player.sendMessage(TextUtils.format("<green>✓ Joined tournament: <yellow>${tournament.name}"))
        
        // Broadcast to all participants
        broadcastToTournament(tournament, "<yellow>${player.name}</yellow> <gray>joined! <yellow>${tournament.participants.size}/${tournament.maxPlayers}")
        
        // Auto-start if full
        if (tournament.participants.size >= tournament.maxPlayers) {
            startTournament(tournamentId)
        }
        
        return true
    }
    
    /**
     * Leave tournament
     */
    fun leaveTournament(player: Player): Boolean {
        val tournamentId = playerTournaments.remove(player.uniqueId) ?: return false
        val tournament = tournaments[tournamentId] ?: return false
        
        if (tournament.status != TournamentStatus.WAITING) {
            player.sendMessage(TextUtils.format("<red>Cannot leave tournament in progress!"))
            playerTournaments[player.uniqueId] = tournamentId // Restore
            return false
        }
        
        tournament.participants.remove(player.uniqueId)
        player.sendMessage(TextUtils.format("<yellow>Left tournament: ${tournament.name}"))
        
        broadcastToTournament(tournament, "<yellow>${player.name}</yellow> <gray>left. <yellow>${tournament.participants.size}/${tournament.maxPlayers}")
        
        return true
    }
    
    /**
     * Start tournament
     */
    fun startTournament(tournamentId: String) {
        val tournament = tournaments[tournamentId] ?: return
        
        if (tournament.status != TournamentStatus.WAITING) return
        if (tournament.participants.size < 2) {
            broadcastToTournament(tournament, "<red>Not enough players to start!")
            return
        }
        
        tournament.status = TournamentStatus.STARTING
        
        plugin.launch {
            // Countdown
            for (i in 5 downTo 1) {
                broadcastToTournament(tournament, "<yellow>Tournament starting in <gold>$i</gold>...")
                delay(1000)
            }
            
            tournament.status = TournamentStatus.IN_PROGRESS
            tournament.startedAt = System.currentTimeMillis()
            
            broadcastToTournament(tournament, "<gradient:#00ff00:#00aa00><bold>⚔ TOURNAMENT STARTED!</bold></gradient>")
            
            // Generate bracket
            generateBracket(tournament)
            
            // Start first round
            startNextRound(tournament)
        }
    }
    
    /**
     * Generate tournament bracket
     */
    private fun generateBracket(tournament: Tournament) {
        val players = tournament.participants.toList().shuffled()
        var round = 1
        
        // Create first round matches
        for (i in players.indices step 2) {
            if (i + 1 < players.size) {
                val match = TournamentMatch(
                    matchId = "${tournament.id}-R${round}-M${i/2}",
                    round = round,
                    player1 = players[i],
                    player2 = players[i + 1]
                )
                tournament.matches.add(match)
            }
        }
        
        plugin.logger.info("Generated bracket for tournament ${tournament.id}: ${tournament.matches.size} matches in round 1")
    }
    
    /**
     * Start next round of matches
     */
    private fun startNextRound(tournament: Tournament) {
        val pendingMatches = tournament.matches.filter { it.status == MatchStatus.PENDING }
        
        if (pendingMatches.isEmpty()) {
            // Tournament finished
            finishTournament(tournament)
            return
        }
        
        val currentRound = pendingMatches.minOf { it.round }
        val roundMatches = pendingMatches.filter { it.round == currentRound }
        
        broadcastToTournament(tournament, "<gradient:#ffd700:#ffaa00><bold>═══ ROUND $currentRound ═══</bold></gradient>")
        
        roundMatches.forEach { match ->
            startMatch(tournament, match)
        }
    }
    
    /**
     * Start a tournament match
     */
    private fun startMatch(tournament: Tournament, match: TournamentMatch) {
        val player1 = plugin.server.getPlayer(match.player1)
        val player2 = plugin.server.getPlayer(match.player2)
        
        if (player1 == null || player2 == null) {
            // Handle disconnect - auto-win for present player
            match.winner = player1?.uniqueId ?: match.player2
            match.status = MatchStatus.COMPLETED
            match.finishedAt = System.currentTimeMillis()
            checkRoundComplete(tournament)
            return
        }
        
        match.status = MatchStatus.IN_PROGRESS
        
        // Start duel
        plugin.duelManager.startTournamentMatch(player1, player2, tournament.kitName, match.matchId)
        
        broadcastToTournament(tournament, "<yellow>${player1.name}</yellow> <gray>vs</gray> <yellow>${player2.name}</yellow> <gray>- Match started!")
    }
    
    /**
     * Handle match completion
     */
    fun handleMatchComplete(matchId: String, winner: UUID) {
        val tournament = tournaments.values.find { t -> 
            t.matches.any { it.matchId == matchId }
        } ?: return
        
        val match = tournament.matches.find { it.matchId == matchId } ?: return
        
        match.winner = winner
        match.status = MatchStatus.COMPLETED
        match.finishedAt = System.currentTimeMillis()
        
        val winnerPlayer = plugin.server.getPlayer(winner)
        broadcastToTournament(tournament, "<gradient:#00ff00:#00aa00>✓</gradient> <yellow>${winnerPlayer?.name}</yellow> <green>won the match!")
        
        checkRoundComplete(tournament)
    }
    
    /**
     * Check if round is complete and start next
     */
    private fun checkRoundComplete(tournament: Tournament) {
        val currentRound = tournament.matches.filter { it.status == MatchStatus.IN_PROGRESS || it.status == MatchStatus.PENDING }
            .minOfOrNull { it.round } ?: run {
            finishTournament(tournament)
            return
        }
        
        val roundMatches = tournament.matches.filter { it.round == currentRound }
        val allComplete = roundMatches.all { it.status == MatchStatus.COMPLETED }
        
        if (allComplete) {
            val winners = roundMatches.mapNotNull { it.winner }
            
            if (winners.size <= 1) {
                finishTournament(tournament)
                return
            }
            
            // Generate next round
            val nextRound = currentRound + 1
            for (i in winners.indices step 2) {
                if (i + 1 < winners.size) {
                    val match = TournamentMatch(
                        matchId = "${tournament.id}-R${nextRound}-M${i/2}",
                        round = nextRound,
                        player1 = winners[i],
                        player2 = winners[i + 1]
                    )
                    tournament.matches.add(match)
                }
            }
            
            plugin.launch {
                delay(5000) // 5 second break between rounds
                startNextRound(tournament)
            }
        }
    }
    
    /**
     * Finish tournament
     */
    private fun finishTournament(tournament: Tournament) {
        tournament.status = TournamentStatus.FINISHED
        tournament.finishedAt = System.currentTimeMillis()
        
        val finalMatch = tournament.matches.filter { it.status == MatchStatus.COMPLETED }
            .maxByOrNull { it.round }
        
        val winner = finalMatch?.winner
        val winnerPlayer = winner?.let { plugin.server.getPlayer(it) }
        
        // Announce winner
        broadcastToAll("<gradient:#ffd700:#ffaa00><bold>═══════════════════════════</bold></gradient>")
        broadcastToAll("<gradient:#ffd700:#ffaa00><bold>🏆 TOURNAMENT FINISHED! 🏆</bold></gradient>")
        broadcastToAll("<yellow>${tournament.name}</yellow>")
        if (winnerPlayer != null) {
            broadcastToAll("<gradient:#00ff00:#00aa00><bold>Winner: ${winnerPlayer.name}</bold></gradient>")
        }
        broadcastToAll("<gradient:#ffd700:#ffaa00><bold>═══════════════════════════</bold></gradient>")
        
        // Give prizes
        if (winner != null && winnerPlayer != null) {
            tournament.prizes[1]?.forEach { command ->
                plugin.server.dispatchCommand(plugin.server.consoleSender, command.replace("{player}", winnerPlayer.name))
            }
        }
        
        // Cleanup
        tournament.participants.forEach { playerTournaments.remove(it) }
        
        plugin.launch {
            delay(30000) // Keep tournament data for 30 seconds
            tournaments.remove(tournament.id)
        }
    }
    
    /**
     * Get tournament by ID
     */
    fun getTournament(id: String): Tournament? = tournaments[id]
    
    /**
     * Get all tournaments
     */
    fun getAllTournaments(): Collection<Tournament> = tournaments.values
    
    /**
     * Get player's tournament
     */
    fun getPlayerTournament(uuid: UUID): Tournament? {
        val id = playerTournaments[uuid] ?: return null
        return tournaments[id]
    }
    
    /**
     * Check if player is in tournament
     */
    fun isInTournament(uuid: UUID): Boolean = playerTournaments.containsKey(uuid)
    
    /**
     * Broadcast message to tournament participants
     */
    private fun broadcastToTournament(tournament: Tournament, message: String) {
        tournament.participants.forEach { uuid ->
            plugin.server.getPlayer(uuid)?.sendMessage(TextUtils.format(message))
        }
    }
    
    /**
     * Broadcast to all online players
     */
    private fun broadcastToAll(message: String) {
        plugin.server.onlinePlayers.forEach { 
            it.sendMessage(TextUtils.format(message))
        }
    }
    
    /**
     * Cleanup player data
     */
    fun cleanupPlayer(uuid: UUID) {
        playerTournaments.remove(uuid)
    }
    
    /**
     * Get memory stats
     */
    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "active_tournaments" to tournaments.size,
            "total_participants" to playerTournaments.size
        )
    }
}
