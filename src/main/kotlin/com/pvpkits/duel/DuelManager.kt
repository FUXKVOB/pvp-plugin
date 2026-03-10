package com.pvpkits.duel

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.HealthUtils
import com.pvpkits.utils.SchedulerUtils
import com.pvpkits.utils.TextUtils
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages duel queues and matches
 */
class DuelManager(private val plugin: PvPKitsPlugin) {

    private val queues = ConcurrentHashMap<String, MutableList<DuelQueueEntry>>()
    private val matches = ConcurrentHashMap<String, DuelMatch>()
    private val playerMatches = ConcurrentHashMap<UUID, DuelMatch>()
    private val playerQueues = ConcurrentHashMap<UUID, String>()
    private val countdownTasks = ConcurrentHashMap<String, org.bukkit.scheduler.BukkitTask>()
    private val duelSpawns = mutableListOf<Pair<Location, Location>>()

    companion object {
        private const val COUNTDOWN_SECONDS = 5
        private const val ROUND_END_DELAY = 3
        private const val SEPARATOR = "<green>============================="
    }

    fun initializeSpawns() {
        val arenaData = plugin.worldManager.getRandomArenaWithSpawns()

        if (arenaData != null) {
            val (arenaName, spawns) = arenaData
            duelSpawns.add(spawns)
            plugin.logger.info("Loaded duel arena: $arenaName")
        } else {
            val world = Bukkit.getWorlds().firstOrNull() ?: return
            val baseLoc = world.spawnLocation

            for (i in 0 until 8) {
                val offset = i * 100
                duelSpawns.add(
                    Location(world, baseLoc.x + offset, baseLoc.y + 10.0, baseLoc.z) to
                        Location(world, baseLoc.x + offset + 20, baseLoc.y + 10.0, baseLoc.z + 20)
                )
            }
            plugin.logger.warning("No arena worlds found, using fallback spawns")
        }
    }

    fun joinQueue(player: Player, kitName: String): Boolean {
        val uuid = player.uniqueId

        if (isInQueue(uuid) || isInMatch(uuid)) {
            send(player, "<red>You are already in a queue or match!")
            return false
        }

        if (plugin.kitManager.getKit(kitName) == null) {
            send(player, "<red>Kit '<white>$kitName</white>' not found!")
            return false
        }

        val kitKey = kitName.lowercase()
        val queue = queues.getOrPut(kitKey) { mutableListOf() }

        if (queue.isNotEmpty()) {
            val opponent = queue.removeAt(0)
            playerQueues.remove(opponent.uuid)
            startMatch(player, opponent, kitName)
            return true
        }

        queue.add(DuelQueueEntry(uuid, player.name, kitName))
        playerQueues[uuid] = kitKey

        sendBlock(
            player,
            listOf(
                SEPARATOR,
                "<gold>Joined <yellow>$kitName</yellow> <gold>queue",
                "<gray>Waiting for an opponent...",
                SEPARATOR
            )
        )
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f)
        return true
    }

    fun leaveQueue(player: Player): Boolean {
        val uuid = player.uniqueId
        val kitKey = playerQueues.remove(uuid) ?: return false

        queues[kitKey]?.removeIf { it.uuid == uuid }
        send(player, "<yellow>Left the queue.")
        return true
    }

    fun isInQueue(uuid: UUID): Boolean = playerQueues.containsKey(uuid)

    fun isInMatch(uuid: UUID): Boolean = playerMatches.containsKey(uuid)

    fun getPlayerMatch(uuid: UUID): DuelMatch? = playerMatches[uuid]

    fun getQueueSize(kitName: String): Int = queues[kitName.lowercase()]?.size ?: 0

    fun getTotalInQueues(): Int = playerQueues.size

    private fun startMatch(player1: Player, entry2: DuelQueueEntry, kitName: String) {
        val player2 = Bukkit.getPlayer(entry2.uuid) ?: return
        startDirectDuel(player1, player2, kitName)
    }

    fun startDirectDuel(player1: Player, player2: Player, kitName: String) {
        val arenaInstance = plugin.improvedArenaManager.getAvailableInstance(kitName)

        if (arenaInstance != null) {
            val matchId = "${player1.uniqueId}_${System.currentTimeMillis()}"

            val match = DuelMatch(
                id = matchId,
                player1 = player1.uniqueId,
                player2 = player2.uniqueId,
                kitName = kitName,
                spawn1 = arenaInstance.template.spawn1,
                spawn2 = arenaInstance.template.spawn2
            )

            matches[matchId] = match
            playerMatches[player1.uniqueId] = match
            playerMatches[player2.uniqueId] = match

            plugin.improvedArenaManager.startMatch(arenaInstance, player1, player2)
            notifyMatchFound(player1, player2, kitName)
            startCountdown(match)
            return
        }

        var spawns = plugin.worldManager.getRandomArenaWithSpawns()?.second
        if (spawns == null) {
            spawns = duelSpawns.firstOrNull()
        }

        if (spawns == null) {
            send(player1, "<red>No arena available!")
            send(player2, "<red>No arena available!")
            return
        }

        val matchId = "${player1.uniqueId}_${System.currentTimeMillis()}"

        val match = DuelMatch(
            id = matchId,
            player1 = player1.uniqueId,
            player2 = player2.uniqueId,
            kitName = kitName,
            spawn1 = spawns.first,
            spawn2 = spawns.second
        )

        matches[matchId] = match
        playerMatches[player1.uniqueId] = match
        playerMatches[player2.uniqueId] = match

        notifyMatchFound(player1, player2, kitName)
        startCountdown(match)
    }

    private fun notifyMatchFound(player1: Player, player2: Player, kitName: String) {
        val lines = listOf(
            SEPARATOR,
            "<gold><bold>Match Found!</bold></gold>",
            "<yellow>Kit: <white>$kitName",
            "<yellow>Opponent: <white>${player1.name}</white> <gray>vs</gray> <white>${player2.name}",
            SEPARATOR
        )

        sendBlock(player1, lines)
        sendBlock(player2, lines)

        player1.playSound(player1.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        player2.playSound(player2.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
    }

    private fun startCountdown(match: DuelMatch) {
        match.state = DuelState.COUNTDOWN

        val player1 = Bukkit.getPlayer(match.player1) ?: return
        val player2 = Bukkit.getPlayer(match.player2) ?: return

        preparePlayerForRound(player1, match)
        preparePlayerForRound(player2, match)

        player1.teleport(match.spawn1)
        player2.teleport(match.spawn2)

        plugin.kitManager.giveKit(player1, match.kitName)
        plugin.kitManager.giveKit(player2, match.kitName)

        var secondsLeft = COUNTDOWN_SECONDS

        val task = SchedulerUtils.runTaskTimer(plugin, 0L, 20L, Runnable {
            if (secondsLeft <= 0) {
                startRound(match)
                countdownTasks.remove(match.id)?.cancel()
                return@Runnable
            }

            val title = when (secondsLeft) {
                5, 4 -> "<yellow><bold>$secondsLeft"
                3, 2 -> "<red><bold>$secondsLeft"
                1 -> "<dark_red><bold>1"
                else -> "<white>$secondsLeft"
            }

            showTitle(
                player1,
                title,
                "<gray>Round ${match.currentRound}",
                0L,
                25L,
                0L
            )
            showTitle(
                player2,
                title,
                "<gray>Round ${match.currentRound}",
                0L,
                25L,
                0L
            )

            player1.playSound(player1.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f)
            player2.playSound(player2.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f)

            secondsLeft--
        })

        countdownTasks[match.id] = task
    }

    private fun preparePlayerForRound(player: Player, match: DuelMatch) {
        player.gameMode = GameMode.ADVENTURE
        HealthUtils.reset(player)
        player.foodLevel = 20
        player.saturation = 20f
        player.fireTicks = 0
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
    }

    private fun startRound(match: DuelMatch) {
        match.state = DuelState.IN_PROGRESS

        val player1 = Bukkit.getPlayer(match.player1) ?: return
        val player2 = Bukkit.getPlayer(match.player2) ?: return

        showTitle(player1, "<green><bold>FIGHT!", "<gray>Round ${match.currentRound}", 0L, 40L, 10L)
        showTitle(player2, "<green><bold>FIGHT!", "<gray>Round ${match.currentRound}", 0L, 40L, 10L)

        player1.playSound(player1.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f)
        player2.playSound(player2.location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f)

        broadcastToMatch(match, "<green><bold>Round ${match.currentRound} - FIGHT!")
    }

    fun handleDeath(deadPlayer: Player): Boolean {
        val match = getPlayerMatch(deadPlayer.uniqueId) ?: return false
        if (match.state != DuelState.IN_PROGRESS) return false

        match.state = DuelState.ROUND_END

        val winner = Bukkit.getPlayer(match.getOpponent(deadPlayer.uniqueId)) ?: return true
        val loser = deadPlayer

        match.addWin(winner.uniqueId)

        broadcastBlock(
            match,
            listOf(
                SEPARATOR,
                "<yellow>${winner.name}</yellow> <green>wins round ${match.currentRound}!",
                "<gray>Score: <yellow>${match.player1Wins}</yellow> <gray>-</gray> <yellow>${match.player2Wins}",
                SEPARATOR
            )
        )

        winner.playSound(winner.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f)
        loser.playSound(loser.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f)

        if (match.isMatchOver()) {
            endMatch(match)
        } else {
            match.currentRound++
            SchedulerUtils.runTaskLater(plugin, (ROUND_END_DELAY * 20).toLong(), Runnable {
                startCountdown(match)
            })
        }

        return true
    }

    private fun endMatch(match: DuelMatch) {
        match.state = DuelState.MATCH_END

        val winnerUUID = match.getWinner()
        val loserUUID = match.getLoser()
        val winner = winnerUUID?.let { Bukkit.getPlayer(it) }
        val loser = loserUUID?.let { Bukkit.getPlayer(it) }

        broadcastBlock(
            match,
            listOf(
                "<gold><bold>==============================",
                "<green><bold>Match Over!",
                "<yellow>Winner: <green><bold>${winner?.name ?: "Unknown"}",
                "<gray>Final Score: <yellow>${match.player1Wins} - ${match.player2Wins}",
                "<gold><bold>=============================="
            )
        )

        winner?.playSound(winner.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        loser?.playSound(loser.location, Sound.ENTITY_WITHER_DEATH, 0.5f, 1.0f)

        if (winner != null && loserUUID != null) {
            plugin.statsManager.recordKill(winner.uniqueId, winner.name, loserUUID, loser?.name ?: "Unknown")
        }

        if (match.isTournamentMatch) {
            handleTournamentMatchEnd(match)
        }

        if (winner != null) {
            val arenaInstance = plugin.improvedArenaManager.getPlayerInstance(winner)
            if (arenaInstance != null) {
                plugin.improvedArenaManager.endMatch(arenaInstance)
            }
        }

        SchedulerUtils.runTaskLater(plugin, (5 * 20).toLong(), Runnable {
            winner?.teleport(Bukkit.getWorlds().first().spawnLocation)
            loser?.teleport(Bukkit.getWorlds().first().spawnLocation)
            cleanupMatch(match)
        })
    }

    private fun broadcastToMatch(match: DuelMatch, message: String) {
        Bukkit.getPlayer(match.player1)?.sendMessage(TextUtils.parseAuto(message))
        Bukkit.getPlayer(match.player2)?.sendMessage(TextUtils.parseAuto(message))
    }

    private fun broadcastBlock(match: DuelMatch, lines: List<String>) {
        Bukkit.getPlayer(match.player1)?.let { sendBlock(it, lines) }
        Bukkit.getPlayer(match.player2)?.let { sendBlock(it, lines) }
    }

    private fun cleanupMatch(match: DuelMatch) {
        matches.remove(match.id)
        playerMatches.remove(match.player1)
        playerMatches.remove(match.player2)
        countdownTasks.remove(match.id)?.cancel()
    }

    fun forceEndMatch(uuid: UUID) {
        val match = playerMatches[uuid] ?: return

        val opponent = match.getOpponent(uuid)
        val opponentPlayer = Bukkit.getPlayer(opponent)

        opponentPlayer?.let {
            send(it, "<red>Your opponent left. You win by forfeit!")
            it.teleport(Bukkit.getWorlds().first().spawnLocation)
        }

        cleanupMatch(match)
    }

    fun cleanupPlayer(uuid: UUID) {
        leaveQueue(Bukkit.getPlayer(uuid) ?: return)

        if (isInMatch(uuid)) {
            forceEndMatch(uuid)
        }
    }

    fun getActiveMatchCount(): Int = matches.size

    fun getQueueInfo(): String {
        return buildString {
            append(TextUtils.legacySection("<gold>=============================\n"))
            append(TextUtils.legacySection("<yellow>Players in queues: <white>${playerQueues.size}\n"))
            append(TextUtils.legacySection("<yellow>Active matches: <white>${matches.size}\n"))
            append(TextUtils.legacySection("<gold>============================="))
        }
    }

    fun startTournamentMatch(player1: Player, player2: Player, kitName: String, tournamentMatchId: String) {
        var spawns = plugin.worldManager.getRandomArenaWithSpawns()?.second

        if (spawns == null) {
            spawns = duelSpawns.firstOrNull()
        }

        if (spawns == null) {
            plugin.logger.warning("No arena available for tournament match!")
            return
        }

        val matchId = "tournament_$tournamentMatchId"

        val match = DuelMatch(
            id = matchId,
            player1 = player1.uniqueId,
            player2 = player2.uniqueId,
            kitName = kitName,
            spawn1 = spawns.first,
            spawn2 = spawns.second,
            isTournamentMatch = true,
            tournamentMatchId = tournamentMatchId
        )

        matches[matchId] = match
        playerMatches[player1.uniqueId] = match
        playerMatches[player2.uniqueId] = match

        notifyMatchFound(player1, player2, kitName)
        startCountdown(match)
    }

    private fun handleTournamentMatchEnd(match: DuelMatch) {
        val winnerUUID = match.getWinner() ?: return

        match.tournamentMatchId?.let { tournamentMatchId ->
            plugin.tournamentManager.handleMatchComplete(tournamentMatchId, winnerUUID)
        }
    }

    private fun send(player: Player, text: String) {
        player.sendMessage(TextUtils.parseAuto(text))
    }

    private fun sendBlock(player: Player, lines: List<String>) {
        player.sendMessage(net.kyori.adventure.text.Component.empty())
        lines.forEach { send(player, it) }
        player.sendMessage(net.kyori.adventure.text.Component.empty())
    }

    private fun showTitle(player: Player, title: String, subtitle: String, fadeInTicks: Long, stayTicks: Long, fadeOutTicks: Long) {
        player.showTitle(
            Title.title(
                TextUtils.parseAuto(title),
                TextUtils.parseAuto(subtitle),
                Title.Times.times(
                    Duration.ofMillis(fadeInTicks * 50),
                    Duration.ofMillis(stayTicks * 50),
                    Duration.ofMillis(fadeOutTicks * 50)
                )
            )
        )
    }
}
