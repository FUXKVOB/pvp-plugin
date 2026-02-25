package com.pvpkits

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.delay
import com.pvpkits.arena.ArenaCommand
import com.pvpkits.arena.ArenaManager
import com.pvpkits.arena.LobbyManager
import com.pvpkits.commands.KitCommand
import com.pvpkits.duel.DuelCommand
import com.pvpkits.duel.DuelListener
import com.pvpkits.duel.DuelManager
import com.pvpkits.gui.KitGUI
import com.pvpkits.nametag.NametagManager
import com.pvpkits.scoreboard.ScoreboardManager
import com.pvpkits.stats.StatsCommand
import com.pvpkits.stats.StatsListener
import com.pvpkits.stats.StatsManager
import com.pvpkits.utils.TextUtils
import com.pvpkits.utils.CoroutineUtils
import com.pvpkits.world.WorldManager
import net.kyori.adventure.text.Component
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent

class PvPKitsPlugin : SuspendingJavaPlugin(), Listener {
    
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
    
    lateinit var lobbyManager: LobbyManager
        private set
    
    lateinit var duelManager: DuelManager
        private set
    
    lateinit var worldManager: WorldManager
        private set

    override suspend fun onEnableAsync() {
        // Initialize coroutine scope for structured concurrency
        CoroutineUtils.initPluginScope(this)
        
        saveDefaultConfig()
        
        // Initialize world manager FIRST (loads lobby and arena worlds)
        worldManager = WorldManager(this)
        worldManager.loadWorlds()
        
        // Load kits asynchronously using optimized IO dispatcher
        kitManager = KitManager(this)
        launch {
            CoroutineUtils.io { kitManager.loadKits() }
        }
        
        kitGUI = KitGUI(this)
        
        nametagManager = NametagManager(this)
        nametagManager.enable()
        
        // Initialize stats system
        statsManager = StatsManager(this)
        launch {
            statsManager.loadStats()
        }
        statsManager.startAutosave()
        
        // Register stats listener
        server.pluginManager.registerEvents(StatsListener(this), this)
        
        // Initialize scoreboard system
        scoreboardManager = ScoreboardManager(this)
        scoreboardManager.startAutoUpdate()
        
        // Initialize arena system
        arenaManager = ArenaManager(this)
        arenaManager.loadArenas()
        
        // Initialize lobby system
        lobbyManager = LobbyManager(this)
        
        // Initialize duel system
        duelManager = DuelManager(this)
        duelManager.initializeSpawns()
        
        // Register duel listener
        server.pluginManager.registerEvents(DuelListener(this), this)
        
        val kitCommand = KitCommand(this)
        getCommand("kit")?.setExecutor(kitCommand)
        getCommand("kit")?.tabCompleter = kitCommand
        getCommand("createkit")?.setExecutor(kitCommand)
        getCommand("deletekit")?.setExecutor(kitCommand)
        
        // Register stats commands
        val statsCommand = StatsCommand(this)
        getCommand("stats")?.setExecutor(statsCommand)
        getCommand("stats")?.tabCompleter = statsCommand
        getCommand("top")?.setExecutor(statsCommand)
        getCommand("top")?.tabCompleter = statsCommand
        
        // Register arena commands
        val arenaCommand = ArenaCommand(this)
        getCommand("arena")?.setExecutor(arenaCommand)
        getCommand("arena")?.tabCompleter = arenaCommand
        getCommand("join")?.setExecutor(arenaCommand)
        getCommand("join")?.tabCompleter = arenaCommand
        getCommand("leave")?.setExecutor(arenaCommand)
        getCommand("queue")?.setExecutor(arenaCommand)
        getCommand("arenas")?.setExecutor(arenaCommand)
        
        // Register duel commands
        val duelCommand = DuelCommand(this)
        getCommand("duel")?.setExecutor(duelCommand)
        getCommand("duel")?.tabCompleter = duelCommand
        getCommand("duelqueue")?.setExecutor(duelCommand)
        getCommand("duelqueue")?.tabCompleter = duelCommand
        
        server.pluginManager.registerEvents(this, this)
        
        // Setup nametags and scoreboards for online players
        server.onlinePlayers.forEach { player ->
            nametagManager.setupPlayer(player)
            scoreboardManager.setupScoreboard(player)
        }
        
        // Log startup info with memory stats
        val memStats = kitManager.getMemoryStats()
        val statsInfo = statsManager.getMemoryStats()
        val arenaStats = arenaManager.getMemoryStats()
        logger.info("╔════════════════════════════════════╗")
        logger.info("║   PvPKits v${description.version} Enabled        ║")
        logger.info("║   Loaded ${memStats["kits_loaded"]} kits                  ║")
        logger.info("║   Players tracked: ${statsInfo["total_players"]}            ║")
        logger.info("║   Arenas: ${arenaStats["arenas_loaded"]}                       ║")
        logger.info("║   Worlds: ${worldManager.getArenaCount()} arenas loaded       ║")
        logger.info("║   Duels: ${duelManager.getActiveMatchCount()} active               ║")
        logger.info("║   Nametags: ${if (config.getBoolean("nametag.enabled")) "ON" else "OFF"}               ║")
        logger.info("║   Stats: ${if (config.getBoolean("stats.enabled")) "ON" else "OFF"}                  ║")
        logger.info("║   Java: ${System.getProperty("java.version")}             ║")
        logger.info("║   Kotlin 2.3.0 + Coroutines        ║")
        logger.info("║   Optimized: Caching + Memory Mgmt ║")
        logger.info("╚════════════════════════════════════╝")
    }

    override suspend fun onDisableAsync() {
        // Save stats before shutdown
        statsManager.saveStats()
        
        // Shutdown database
        statsManager.shutdown()
        
        // Save arenas
        arenaManager.saveArenas()
        
        // Clean up all coroutines to prevent memory leaks
        CoroutineUtils.cancelPluginScope()
        
        // Clean up all player data
        server.onlinePlayers.forEach { player ->
            kitManager.cleanupPlayer(player.uniqueId)
            kitGUI.cleanup(player)
            scoreboardManager.removeScoreboard(player)
            arenaManager.cleanupPlayer(player.uniqueId)
            lobbyManager.cleanupPlayer(player.uniqueId)
            duelManager.cleanupPlayer(player.uniqueId)
        }
        
        // Clear all caches
        kitGUI.clearAllCache()
        
        logger.info("PvPKits plugin disabled - all resources cleaned up!")
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? org.bukkit.entity.Player ?: return
        
        // Check if it's our GUI by checking the inventory holder
        val inventory = event.inventory
        if (inventory.holder != null) return // Not our GUI
        
        // Additional check: verify it's the kit GUI by checking title
        val viewTitle = event.view.title
        val configTitle = config.getString("gui.title") ?: "⚔ PvP Kits"
        
        // Simple string contains check instead of Component comparison
        if (!viewTitle.contains("PvP Kits") && !viewTitle.contains("Kits")) return
        
        event.isCancelled = true
        
        val clickedItem = event.currentItem ?: return
        
        kitGUI.handleClick(player, event.slot, clickedItem)
    }
    
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        
        nametagManager.onPlayerJoin(player)
        scoreboardManager.setupScoreboard(player)
        
        // Teleport to lobby on join
        if (config.getBoolean("lobby.teleport-on-join", true)) {
            worldManager.teleportToLobby(player)
        }
        
        // Give compass for kit selection if not in arena or duel
        if (!arenaManager.isInArena(player) && !duelManager.isInMatch(player.uniqueId)) {
            giveKitCompass(player)
        }
    }
    
    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        // Respawn in arena if player was in one
        if (arenaManager.isInArena(event.player)) {
            launch {
                delay(100) // Small delay using coroutines
                arenaManager.respawnInArena(event.player)
            }
        }
    }
    
    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        // Auto-respawn in arena
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
        meta?.setDisplayName("§6§l⚔ Kit Selection")
        meta?.lore = listOf("§7Right-click to select a kit!")
        compass.itemMeta = meta
        player.inventory.setItem(4, compass)
    }
    
    @EventHandler
    fun onPlayerInteract(event: org.bukkit.event.player.PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return
        
        // Check if it's our kit selection compass
        if (item.type == org.bukkit.Material.COMPASS) {
            val displayName = item.itemMeta?.displayName ?: return
            // Check for both English and legacy names
            if (displayName.contains("Kit Selection") || displayName.contains("⚔")) {
                event.isCancelled = true
                kitGUI.openKitMenu(player)
            }
        }
    }
    
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        // Clean up player data to prevent memory leaks
        kitManager.cleanupPlayer(player.uniqueId)
        kitGUI.cleanup(player)
        nametagManager.onPlayerQuit(player)
        scoreboardManager.removeScoreboard(player)
        arenaManager.cleanupPlayer(player.uniqueId)
        lobbyManager.cleanupPlayer(player.uniqueId)
        duelManager.cleanupPlayer(player.uniqueId)
    }
}
