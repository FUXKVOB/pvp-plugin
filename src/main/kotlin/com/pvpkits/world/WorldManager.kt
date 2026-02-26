package com.pvpkits.world

import com.pvpkits.PvPKitsPlugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import java.io.File

/**
 * Manages lobby and arena worlds
 * 
 * Best Practices 2026:
 * - Loads existing worlds instead of generating new ones
 * - Supports nested world folders (e.g., lobby/helloween)
 * - Validates world data before loading
 * - Proper error handling and logging
 */
class WorldManager(private val plugin: PvPKitsPlugin) {
    
    private var lobbyWorld: World? = null
    private val arenaWorlds = mutableMapOf<String, World>()
    
    // Spawn locations
    private var lobbySpawn: Location? = null
    private val arenaSpawns = mutableMapOf<String, Pair<Location, Location>>()
    
    companion object {
        const val LOBBY_WORLD = "lobby"
        const val ARENA_PREFIX = "arena"
    }
    
    /**
     * Load all worlds on startup
     * Supports nested folders like lobby/helloween or arena1/helloween
     */
    fun loadWorlds() {
        val serverFolder = Bukkit.getWorldContainer()
        
        plugin.logger.info("Loading worlds from: ${serverFolder.absolutePath}")
        
        // Load lobby world
        lobbyWorld = loadLobbyWorld(serverFolder)
        
        // Load arena worlds
        loadArenaWorlds(serverFolder)
        
        // Load spawn from config or use world spawn
        loadLobbySpawnFromConfig()
        
        // Setup arena spawns
        setupArenaSpawns()
        
        plugin.logger.info("World loading complete:")
        plugin.logger.info("  - Lobby: ${lobbyWorld?.name ?: "NOT FOUND"}")
        plugin.logger.info("  - Arenas: ${arenaWorlds.size}")
        arenaWorlds.forEach { (name, world) ->
            plugin.logger.info("    * $name -> ${world.name}")
        }
    }
    
    /**
     * Load lobby world, checking for nested folders
     * Example: D:\server\lobby\helloween -> loads "helloween" world
     */
    private fun loadLobbyWorld(serverFolder: File): World? {
        val lobbyFolder = File(serverFolder, LOBBY_WORLD)
        
        // Check if lobby folder exists
        if (!lobbyFolder.exists() || !lobbyFolder.isDirectory) {
            plugin.logger.warning("Lobby folder not found: ${lobbyFolder.absolutePath}")
            
            // Try to find existing lobby world by name
            return Bukkit.getWorlds().firstOrNull { 
                it.name.equals(LOBBY_WORLD, ignoreCase = true) 
            }
        }
        
        // Check if lobby folder has level.dat directly
        if (File(lobbyFolder, "level.dat").exists()) {
            plugin.logger.info("Found lobby world directly: $LOBBY_WORLD")
            return loadOrGetWorld(LOBBY_WORLD)
        }
        
        // Check for nested world folder (like lobby/helloween)
        val nestedWorlds = lobbyFolder.listFiles()?.filter { 
            it.isDirectory && File(it, "level.dat").exists() 
        }
        
        if (!nestedWorlds.isNullOrEmpty()) {
            val worldFolder = nestedWorlds.first()
            val worldName = worldFolder.name
            
            plugin.logger.info("Found nested lobby world: $LOBBY_WORLD/$worldName")
            plugin.logger.info("Loading world: $worldName")
            
            return loadOrGetWorld(worldName)
        }
        
        plugin.logger.warning("No valid world found in lobby folder!")
        return null
    }
    
    /**
     * Load all arena worlds
     * Supports nested folders like arena1/helloween
     */
    private fun loadArenaWorlds(serverFolder: File) {
        val arenaFolders = serverFolder.listFiles()?.filter { 
            it.isDirectory && it.name.startsWith(ARENA_PREFIX)
        } ?: return
        
        arenaFolders.forEach { arenaFolder ->
            val arenaName = arenaFolder.name
            
            // Check if arena folder has level.dat directly
            if (File(arenaFolder, "level.dat").exists()) {
                plugin.logger.info("Found arena world directly: $arenaName")
                loadOrGetWorld(arenaName)?.let { world ->
                    arenaWorlds[arenaName] = world
                }
                return@forEach
            }
            
            // Check for nested world folder (like arena1/helloween)
            val nestedWorlds = arenaFolder.listFiles()?.filter { 
                it.isDirectory && File(it, "level.dat").exists() 
            }
            
            if (!nestedWorlds.isNullOrEmpty()) {
                val worldFolder = nestedWorlds.first()
                val worldName = worldFolder.name
                
                plugin.logger.info("Found nested arena world: $arenaName/$worldName")
                plugin.logger.info("Loading world: $worldName")
                
                loadOrGetWorld(worldName)?.let { world ->
                    arenaWorlds[arenaName] = world
                }
            }
        }
    }
    
    /**
     * Load or get existing world
     * This prevents creating new worlds if they already exist
     */
    private fun loadOrGetWorld(worldName: String): World? {
        // Check if world is already loaded
        Bukkit.getWorld(worldName)?.let { 
            plugin.logger.info("World already loaded: $worldName")
            return it 
        }
        
        // Validate world folder exists
        val worldFolder = File(Bukkit.getWorldContainer(), worldName)
        if (!worldFolder.exists() || !worldFolder.isDirectory) {
            plugin.logger.warning("World folder not found: ${worldFolder.absolutePath}")
            return null
        }
        
        // Validate level.dat exists
        val levelDat = File(worldFolder, "level.dat")
        if (!levelDat.exists()) {
            plugin.logger.warning("Invalid world (no level.dat): $worldName")
            return null
        }
        
        // Load the world
        return try {
            plugin.logger.info("Loading world: $worldName from ${worldFolder.absolutePath}")
            val world = Bukkit.createWorld(WorldCreator.name(worldName))
            
            if (world != null) {
                plugin.logger.info("✓ Successfully loaded world: ${world.name}")
            } else {
                plugin.logger.severe("✗ Failed to load world: $worldName (returned null)")
            }
            
            world
        } catch (e: Exception) {
            plugin.logger.severe("✗ Exception loading world $worldName: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Load lobby spawn from config
     */
    private fun loadLobbySpawnFromConfig() {
        val config = plugin.config
        val worldName = config.getString("lobby.spawn.world")
        
        if (worldName != null) {
            val world = Bukkit.getWorld(worldName) ?: lobbyWorld
            
            if (world != null) {
                val x = config.getDouble("lobby.spawn.x", 0.0)
                val y = config.getDouble("lobby.spawn.y", 64.0)
                val z = config.getDouble("lobby.spawn.z", 0.0)
                val yaw = config.getDouble("lobby.spawn.yaw", 0.0).toFloat()
                val pitch = config.getDouble("lobby.spawn.pitch", 0.0).toFloat()
                
                lobbySpawn = Location(world, x, y, z, yaw, pitch)
                plugin.logger.info("Loaded lobby spawn from config: $x, $y, $z in ${world.name}")
                return
            }
        }
        
        // Fallback to world spawn
        lobbyWorld?.let { world ->
            lobbySpawn = world.spawnLocation
            plugin.logger.info("Using world spawn for lobby: ${world.name}")
        }
    }
    
    /**
     * Setup spawn points for arenas
     */
    private fun setupArenaSpawns() {
        arenaWorlds.forEach { (name, world) ->
            val spawn = world.spawnLocation
            
            // Create two spawn points for duels (opposite sides)
            val spawn1 = Location(world, spawn.x + 5, spawn.y, spawn.z)
            val spawn2 = Location(world, spawn.x - 5, spawn.y, spawn.z)
            
            arenaSpawns[name] = spawn1 to spawn2
            plugin.logger.info("Setup spawns for arena: $name")
        }
    }
    
    /**
     * Get lobby world
     */
    fun getLobbyWorld(): World? {
        // Return cached lobby world
        lobbyWorld?.let { return it }
        
        // Try to find by name
        Bukkit.getWorld(LOBBY_WORLD)?.let { 
            lobbyWorld = it
            return it 
        }
        
        // Try to find world with lobby in name
        return Bukkit.getWorlds().firstOrNull { 
            it.name.contains("lobby", ignoreCase = true) 
        }?.also {
            lobbyWorld = it
        }
    }
    
    /**
     * Get lobby spawn location
     */
    fun getLobbySpawn(): Location? {
        // Return cached spawn
        lobbySpawn?.let { return it }
        
        // Get from world
        val world = getLobbyWorld() ?: return null
        lobbySpawn = world.spawnLocation
        return lobbySpawn
    }
    
    /**
     * Get all arena worlds
     */
    fun getArenaWorlds(): Collection<World> = arenaWorlds.values
    
    /**
     * Get arena world by name
     */
    fun getArenaWorld(name: String): World? = arenaWorlds[name]
    
    /**
     * Get random arena world
     */
    fun getRandomArenaWorld(): World? {
        return if (arenaWorlds.isNotEmpty()) arenaWorlds.values.random() else null
    }
    
    /**
     * Get arena spawns
     */
    fun getArenaSpawns(arenaName: String): Pair<Location, Location>? {
        return arenaSpawns[arenaName]
    }
    
    /**
     * Get random arena with spawns
     */
    fun getRandomArenaWithSpawns(): Pair<String, Pair<Location, Location>>? {
        if (arenaSpawns.isEmpty()) return null
        val entry = arenaSpawns.entries.random()
        return entry.key to entry.value
    }
    
    /**
     * Teleport player to lobby
     */
    fun teleportToLobby(player: Player): Boolean {
        val spawn = getLobbySpawn()
        if (spawn == null) {
            player.sendMessage("§cLobby world not found!")
            return false
        }
        
        player.teleport(spawn)
        player.gameMode = org.bukkit.GameMode.ADVENTURE
        player.inventory.clear()
        player.health = player.maxHealth
        player.foodLevel = 20
        
        return true
    }
    
    /**
     * Teleport player to arena
     */
    fun teleportToArena(player: Player, arenaName: String): Boolean {
        val world = getArenaWorld(arenaName) ?: return false
        player.teleport(world.spawnLocation)
        return true
    }
    
    /**
     * Set lobby spawn location
     */
    fun setLobbySpawn(location: Location) {
        lobbySpawn = location.clone()
        plugin.config.set("lobby.spawn.world", location.world?.name)
        plugin.config.set("lobby.spawn.x", location.x)
        plugin.config.set("lobby.spawn.y", location.y)
        plugin.config.set("lobby.spawn.z", location.z)
        plugin.config.set("lobby.spawn.yaw", location.yaw)
        plugin.config.set("lobby.spawn.pitch", location.pitch)
        plugin.saveConfig()
    }
    
    /**
     * Check if world is an arena
     */
    fun isArenaWorld(worldName: String): Boolean {
        return worldName.startsWith(ARENA_PREFIX)
    }
    
    /**
     * Get arena count
     */
    fun getArenaCount(): Int = arenaWorlds.size
    
    /**
     * Get info string
     */
    fun getInfo(): String {
        return buildString {
            append("§6════════ World Info ════════\n")
            append("§eLobby: ${if (getLobbyWorld() != null) "§aLoaded" else "§cNot found"}\n")
            append("§eArenas: §f${arenaWorlds.size}\n")
            arenaWorlds.keys.forEach { name ->
                append("§7  - $name\n")
            }
            append("§6═══════════════════════════")
        }
    }
}
