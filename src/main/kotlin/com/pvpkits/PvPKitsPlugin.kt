package com.pvpkits

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.launch
import com.pvpkits.analytics.HeatmapCommand
import com.pvpkits.analytics.HeatmapManager
import com.pvpkits.arena.ArenaCommand
import com.pvpkits.arena.ArenaManager
import com.pvpkits.arena.ImprovedArenaManager
import com.pvpkits.arena.LobbyManager
import com.pvpkits.combat.CombatMechanicsManager
import com.pvpkits.commands.KitCommand
import com.pvpkits.cosmetics.CosmeticsCommand
import com.pvpkits.cosmetics.CosmeticsManager
import com.pvpkits.database.BatchStatsManager
import com.pvpkits.database.DatabaseManager
import com.pvpkits.duel.DuelCommand
import com.pvpkits.duel.DuelListener
import com.pvpkits.duel.DuelManager
import com.pvpkits.gui.KitGUI
import com.pvpkits.gui.KitMenuHolder
import com.pvpkits.matchmaking.MatchmakingManager
import com.pvpkits.nametag.NametagManager
import com.pvpkits.party.PartyCommand
import com.pvpkits.party.PartyManager
import com.pvpkits.rating.RatingCommand
import com.pvpkits.rating.RatingManager
import com.pvpkits.replay.ReplayManager
import com.pvpkits.scoreboard.ScoreboardManager
import com.pvpkits.spectator.SpectatorCommand
import com.pvpkits.spectator.SpectatorListener
import com.pvpkits.spectator.SpectatorManager
import com.pvpkits.stats.EnhancedStatsManager
import com.pvpkits.stats.StatsCommand
import com.pvpkits.stats.StatsListener
import com.pvpkits.stats.StatsManager
import com.pvpkits.tournament.TournamentCommand
import com.pvpkits.tournament.TournamentManager
import com.pvpkits.utils.CoroutineUtils
import com.pvpkits.utils.ItemKeys
import com.pvpkits.utils.TextUtils
import com.pvpkits.world.WorldManager
import kotlinx.coroutines.delay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.persistence.PersistentDataType

class PvPKitsPlugin : SuspendingJavaPlugin(), Listener {

    lateinit var databaseManager: DatabaseManager
        private set

    lateinit var itemKeys: ItemKeys
        private set

    lateinit var kitManager: KitManager
        private set

    lateinit var kitGUI: KitGUI
        private set

    lateinit var nametagManager: NametagManager
        private set

    lateinit var statsManager: StatsManager
        private set

    lateinit var scoreboardManager: ScoreboardManager
        private set

    lateinit var arenaManager: ArenaManager
        private set

    lateinit var improvedArenaManager: ImprovedArenaManager
        private set

    lateinit var lobbyManager: LobbyManager
        private set

    lateinit var duelManager: DuelManager
        private set

    lateinit var worldManager: WorldManager
        private set

    lateinit var spectatorManager: SpectatorManager
        private set

    lateinit var tournamentManager: TournamentManager
        private set

    lateinit var ratingManager: RatingManager
        private set

    lateinit var replayManager: ReplayManager
        private set

    lateinit var cosmeticsManager: CosmeticsManager
        private set

    lateinit var partyManager: PartyManager
        private set

    lateinit var enhancedStatsManager: EnhancedStatsManager
        private set

    lateinit var matchmakingManager: MatchmakingManager
        private set

    lateinit var combatMechanicsManager: CombatMechanicsManager
        private set

    lateinit var heatmapManager: HeatmapManager
        private set

    lateinit var batchStatsManager: BatchStatsManager
        private set

    lateinit var antiCheatManager: com.pvpkits.anticheat.ModernAntiCheatManager
        private set

    lateinit var replayViewerGUI: com.pvpkits.replay.ReplayViewerGUI
        private set

    override suspend fun onEnableAsync() {
        CoroutineUtils.initPluginScope(this)
        itemKeys = ItemKeys(this)

        saveDefaultConfig()

        databaseManager = DatabaseManager(this)
        databaseManager.initialize()

        worldManager = WorldManager(this)
        worldManager.loadWorlds()

        kitManager = KitManager(this)
        CoroutineUtils.io { kitManager.loadKits() }

        kitGUI = KitGUI(this)

        nametagManager = NametagManager(this)
        nametagManager.enable()

        statsManager = StatsManager(this, databaseManager)
        statsManager.loadStats()
        statsManager.startAutosave()
        server.pluginManager.registerEvents(StatsListener(this), this)

        scoreboardManager = ScoreboardManager(this)
        scoreboardManager.startAutoUpdate()

        arenaManager = ArenaManager(this)
        arenaManager.loadArenas()

        improvedArenaManager = ImprovedArenaManager(this)
        improvedArenaManager.loadTemplates()

        lobbyManager = LobbyManager(this)

        duelManager = DuelManager(this)
        duelManager.initializeSpawns()
        server.pluginManager.registerEvents(DuelListener(this), this)

        spectatorManager = SpectatorManager(this)
        server.pluginManager.registerEvents(SpectatorListener(this), this)

        tournamentManager = TournamentManager(this)

        ratingManager = RatingManager(this, databaseManager)
        ratingManager.initialize()

        replayManager = ReplayManager(this)
        cosmeticsManager = CosmeticsManager(this)
        partyManager = PartyManager(this)

        enhancedStatsManager = EnhancedStatsManager(this, databaseManager)
        enhancedStatsManager.initialize()

        batchStatsManager = BatchStatsManager(this)
        antiCheatManager = com.pvpkits.anticheat.ModernAntiCheatManager(this)
        replayViewerGUI = com.pvpkits.replay.ReplayViewerGUI(this)

        try {
            com.pvpkits.metrics.BStatsMetrics(this).initialize()
        } catch (e: Exception) {
            logger.warning("Failed to initialize bStats: ${e.message}")
        }

        matchmakingManager = MatchmakingManager(this)
        combatMechanicsManager = CombatMechanicsManager(this)
        heatmapManager = HeatmapManager(this)

        val kitCommand = KitCommand(this)
        getCommand("kit")?.setExecutor(kitCommand)
        getCommand("kit")?.tabCompleter = kitCommand
        getCommand("createkit")?.setExecutor(kitCommand)
        getCommand("deletekit")?.setExecutor(kitCommand)

        val statsCommand = StatsCommand(this)
        getCommand("stats")?.setExecutor(statsCommand)
        getCommand("stats")?.tabCompleter = statsCommand
        getCommand("top")?.setExecutor(statsCommand)
        getCommand("top")?.tabCompleter = statsCommand

        val arenaCommand = ArenaCommand(this)
        getCommand("arena")?.setExecutor(arenaCommand)
        getCommand("arena")?.tabCompleter = arenaCommand
        getCommand("join")?.setExecutor(arenaCommand)
        getCommand("join")?.tabCompleter = arenaCommand
        getCommand("leave")?.setExecutor(arenaCommand)
        getCommand("queue")?.setExecutor(arenaCommand)
        getCommand("arenas")?.setExecutor(arenaCommand)

        val duelCommand = DuelCommand(this)
        getCommand("duelqueue")?.setExecutor(duelCommand)
        getCommand("duelqueue")?.tabCompleter = duelCommand

        val spectatorCommand = SpectatorCommand(this)
        getCommand("spectate")?.setExecutor(spectatorCommand)
        getCommand("spectate")?.tabCompleter = spectatorCommand
        getCommand("spec")?.setExecutor(spectatorCommand)
        getCommand("spec")?.tabCompleter = spectatorCommand
        getCommand("stopspectating")?.setExecutor(spectatorCommand)
        getCommand("stopspec")?.setExecutor(spectatorCommand)

        val tournamentCommand = TournamentCommand(this)
        getCommand("tournament")?.setExecutor(tournamentCommand)
        getCommand("tournament")?.tabCompleter = tournamentCommand

        val ratingCommand = RatingCommand(this)
        getCommand("rating")?.setExecutor(ratingCommand)
        getCommand("rating")?.tabCompleter = ratingCommand
        getCommand("elo")?.setExecutor(ratingCommand)
        getCommand("elo")?.tabCompleter = ratingCommand
        getCommand("leaderboard")?.setExecutor(ratingCommand)
        getCommand("leaderboard")?.tabCompleter = ratingCommand

        val partyCommand = PartyCommand(this)
        getCommand("party")?.setExecutor(partyCommand)
        getCommand("party")?.tabCompleter = partyCommand
        getCommand("duel")?.setExecutor(partyCommand)
        getCommand("duel")?.tabCompleter = partyCommand
        getCommand("challenge")?.setExecutor(partyCommand)
        getCommand("challenge")?.tabCompleter = partyCommand

        val cosmeticsCommand = CosmeticsCommand(this)
        getCommand("cosmetics")?.setExecutor(cosmeticsCommand)
        getCommand("cosmetics")?.tabCompleter = cosmeticsCommand

        val heatmapCommand = HeatmapCommand(this)
        getCommand("heatmap")?.setExecutor(heatmapCommand)
        getCommand("heatmap")?.tabCompleter = heatmapCommand

        val replayCommand = com.pvpkits.replay.ReplayCommand(this)
        getCommand("replay")?.setExecutor(replayCommand)
        getCommand("replay")?.tabCompleter = replayCommand

        server.pluginManager.registerEvents(this, this)

        server.scheduler.runTaskTimer(this, Runnable {
            cosmeticsManager.updateTrails()
        }, 0L, 1L)

        server.scheduler.runTaskTimer(this, Runnable {
            partyManager.cleanupExpired()
        }, 600L, 600L)

        server.onlinePlayers.forEach { player ->
            nametagManager.setupPlayer(player)
            scoreboardManager.setupScoreboard(player)
        }

        val memStats = kitManager.getMemoryStats()
        val statsInfo = statsManager.getMemoryStats()
        val arenaStats = arenaManager.getMemoryStats()
        val improvedArenaStats = improvedArenaManager.getMemoryStats()
        val matchmakingStats = matchmakingManager.getStats()
        val combatStats = combatMechanicsManager.getStats()
        val heatmapStats = heatmapManager.getGlobalStats()
        val batchStats = batchStatsManager.getQueueStats()
        val antiCheatStats = antiCheatManager.getStats()
        val componentCacheStats = com.pvpkits.utils.ComponentCache.getCacheStats()

        logger.info("Loaded ${memStats["kits_loaded"]} kits")
        logger.info("Players tracked: ${statsInfo["total_players"]}")
        logger.info("Arenas: ${arenaStats["arenas_loaded"]}")
        logger.info("Arena Templates: ${improvedArenaStats["templates"]}")
        logger.info("MMR Queue: ${matchmakingStats["total_in_queue"]}")
        logger.info("Combat Tracking: ${combatStats["active_combos"]}")
        logger.info("Heatmap: ${heatmapStats["tracked_arenas"]}")
        logger.info("Batch Queue: ${batchStats["total_pending"]}")
        logger.info("Component Cache: ${componentCacheStats["size"]}")
        logger.info("Anti-Cheat Players: ${antiCheatStats["tracked_players"]}")
    }

    override suspend fun onDisableAsync() {
        batchStatsManager.shutdown()
        statsManager.saveStats()
        statsManager.shutdown()
        ratingManager.shutdown()
        databaseManager.shutdown()
        arenaManager.saveArenas()
        improvedArenaManager.saveTemplates()
        CoroutineUtils.cancelPluginScope()

        server.onlinePlayers.forEach { player ->
            kitManager.cleanupPlayer(player.uniqueId)
            kitGUI.cleanup(player)
            scoreboardManager.removeScoreboard(player)
            arenaManager.cleanupPlayer(player.uniqueId)
            improvedArenaManager.cleanupPlayer(player.uniqueId)
            lobbyManager.cleanupPlayer(player.uniqueId)
            duelManager.cleanupPlayer(player.uniqueId)
            spectatorManager.cleanupPlayer(player.uniqueId)
            tournamentManager.cleanupPlayer(player.uniqueId)
            partyManager.cleanupPlayer(player.uniqueId)
            cosmeticsManager.cleanupPlayer(player.uniqueId)
            matchmakingManager.cleanupPlayer(player.uniqueId)
            combatMechanicsManager.cleanupPlayer(player.uniqueId)
            antiCheatManager.cleanupPlayer(player.uniqueId)
        }

        kitGUI.clearAllCache()
        com.pvpkits.utils.ComponentCache.clearCache()
        logger.info("PvPKits plugin disabled - all resources cleaned up!")
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        val inventory = event.view.topInventory
        if (inventory.holder !is KitMenuHolder) return
        if (event.clickedInventory != inventory) return

        event.isCancelled = true
        val clickedItem = event.currentItem ?: return
        kitGUI.handleClick(player, event.slot, clickedItem)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        nametagManager.onPlayerJoin(player)
        scoreboardManager.setupScoreboard(player)

        if (config.getBoolean("lobby.teleport-on-join", true)) {
            worldManager.teleportToLobby(player)
        }

        if (!arenaManager.isInArena(player) && !duelManager.isInMatch(player.uniqueId)) {
            giveKitCompass(player)
        }
    }

    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        if (arenaManager.isInArena(event.player)) {
            launch {
                delay(100)
                arenaManager.respawnInArena(event.player)
            }
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (arenaManager.isInArena(event.entity)) {
            launch {
                delay(50)
                event.entity.spigot().respawn()
            }
        }
    }

    private fun giveKitCompass(player: org.bukkit.entity.Player) {
        val compass = org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS)
        val meta = compass.itemMeta
        meta?.displayName(TextUtils.parseAuto("<gold><bold>Kit Selection"))
        meta?.lore(TextUtils.lines("<gray>Right-click to select a kit!"))
        meta?.persistentDataContainer?.set(itemKeys.kitCompass, PersistentDataType.BYTE, 1)
        compass.itemMeta = meta
        player.inventory.setItem(4, compass)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val item = event.item ?: return
        if (item.type != org.bukkit.Material.COMPASS) return

        val isKitCompass = item.itemMeta?.persistentDataContainer
            ?.has(itemKeys.kitCompass, PersistentDataType.BYTE) == true
        if (!isKitCompass) return

        event.isCancelled = true
        kitGUI.openKitMenu(event.player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        kitManager.cleanupPlayer(player.uniqueId)
        kitGUI.cleanup(player)
        nametagManager.onPlayerQuit(player)
        scoreboardManager.removeScoreboard(player)
        arenaManager.cleanupPlayer(player.uniqueId)
        lobbyManager.cleanupPlayer(player.uniqueId)
        duelManager.cleanupPlayer(player.uniqueId)
        spectatorManager.cleanupPlayer(player.uniqueId)
        tournamentManager.cleanupPlayer(player.uniqueId)
        partyManager.cleanupPlayer(player.uniqueId)
        cosmeticsManager.cleanupPlayer(player.uniqueId)
        antiCheatManager.cleanupPlayer(player.uniqueId)
    }
}
