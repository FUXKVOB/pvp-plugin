package com.pvpkits.replay

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.CoroutineUtils
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import java.io.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ReplayManager(private val plugin: PvPKitsPlugin) {
    
    private val activeRecorders = ConcurrentHashMap<String, ReplayRecorder>()
    private val replayCache = ConcurrentHashMap<String, ReplayData>()
    private val playerReplays = ConcurrentHashMap<UUID, MutableList<String>>() // player -> replay IDs
    
    private val replayFolder = plugin.dataFolder.resolve("replays").apply { mkdirs() }
    private val maxReplaysPerPlayer = 10
    
    /**
     * Start recording a match
     */
    fun startRecording(
        matchId: String,
        player1: Player,
        player2: Player,
        kitName: String
    ): ReplayRecorder {
        val recorder = ReplayRecorder(
            matchId = matchId,
            player1 = player1.uniqueId,
            player2 = player2.uniqueId,
            player1Name = player1.name,
            player2Name = player2.name,
            kitName = kitName
        )
        
        activeRecorders[matchId] = recorder
        plugin.logger.info("Started replay recording for match: $matchId")
        
        return recorder
    }
    
    /**
     * Stop recording and save
     */
    fun stopRecording(matchId: String, winner: UUID) {
        val recorder = activeRecorders.remove(matchId) ?: return
        
        CoroutineUtils.pluginScope.launch {
            val replayData = recorder.finish(winner)
            
            // Save to disk
            saveReplay(replayData)
            
            // Cache in memory
            replayCache[matchId] = replayData
            
            // Add to player lists
            playerReplays.getOrPut(replayData.player1) { mutableListOf() }.add(matchId)
            playerReplays.getOrPut(replayData.player2) { mutableListOf() }.add(matchId)
            
            // Cleanup old replays
            cleanupOldReplays(replayData.player1)
            cleanupOldReplays(replayData.player2)
            
            plugin.logger.info("Saved replay: $matchId (${recorder.getFrameCount()} frames)")
        }
    }
    
    /**
     * Get recorder for match
     */
    fun getRecorder(matchId: String): ReplayRecorder? = activeRecorders[matchId]
    
    /**
     * Save replay to disk
     */
    private suspend fun saveReplay(replay: ReplayData) {
        CoroutineUtils.io {
            val file = replayFolder.resolve("${replay.id}.replay")
            ObjectOutputStream(FileOutputStream(file)).use { oos ->
                oos.writeObject(replay)
            }
        }
    }
    
    /**
     * Load replay from disk
     */
    suspend fun loadReplay(replayId: String): ReplayData? {
        // Check cache first
        replayCache[replayId]?.let { return it }
        
        return CoroutineUtils.io {
            val file = replayFolder.resolve("$replayId.replay")
            if (!file.exists()) return@io null
            
            try {
                ObjectInputStream(FileInputStream(file)).use { ois ->
                    val replay = ois.readObject() as ReplayData
                    replayCache[replayId] = replay
                    replay
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load replay $replayId: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Get player's replays
     */
    fun getPlayerReplays(uuid: UUID): List<String> {
        return playerReplays[uuid]?.toList() ?: emptyList()
    }
    
    /**
     * Cleanup old replays (keep only last N)
     */
    private fun cleanupOldReplays(uuid: UUID) {
        val replays = playerReplays[uuid] ?: return
        
        if (replays.size > maxReplaysPerPlayer) {
            val toRemove = replays.take(replays.size - maxReplaysPerPlayer)
            toRemove.forEach { replayId ->
                // Remove from disk
                replayFolder.resolve("$replayId.replay").delete()
                // Remove from cache
                replayCache.remove(replayId)
            }
            replays.removeAll(toRemove.toSet())
        }
    }
    
    /**
     * Delete replay
     */
    suspend fun deleteReplay(replayId: String) {
        CoroutineUtils.io {
            replayFolder.resolve("$replayId.replay").delete()
            replayCache.remove(replayId)
            
            // Remove from player lists
            playerReplays.values.forEach { it.remove(replayId) }
        }
    }
    
    /**
     * Get memory stats
     */
    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "active_recordings" to activeRecorders.size,
            "cached_replays" to replayCache.size,
            "total_replay_files" to (replayFolder.listFiles()?.size ?: 0)
        )
    }
}
