package com.pvpkits.world

import com.pvpkits.PvPKitsPlugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.entity.Player
import java.io.File

/**
 * Manages lobby and arena worlds
 */
class WorldManager(private val plugin: PvPKitsPlugin) {
    
    private val lobbyWorld: World? = null
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
     */
    fun loadWorlds() {
        val dataFolder = Bukkit.getWorldContainer()
        
        // Try to load lobby world - check for subfolders like lobby/helloween
        var lobbyLoaded = loadLobbyWorld(dataFolder)
        
        // Load arena worlds - check for subfolders like arena1/helloween
        dataFolder.listFiles()?.filter { 
            it.isDirectory && it.name.startsWith(ARENA_PREFIX) 
        }?.forEach { folder ->
            loadArenaWorldWithSubfolder(folder)
        }
        
        // Set lobby spawn
        Bukkit.getWorld(LOBBY_WORLD)?.let { world ->
            lobbySpawn = world.spawnLocation
            plugin.logger.info("Lobby world loaded: ${world.name}")
        } ?: run {
            // Try to find any world that could be lobby
            Bukkit.getWorlds().firstOrNull { it.name.contains("lobby", ignoreCase = true) }?.let { world ->
                lobbySpawn = world.spawnLocation
                plugin.logger.info("Lobby world found: ${world.name}")
            }
        }
        
        // Setup arena spawns
        setupArenaSpawns()
        
        plugin.logger.info("Loaded ${arenaWorlds.size} arena worlds")
    }
    
    /**
     * Load lobby world, checking for subfolders
     */
    private fun loadLobbyWorld(dataFolder: File): Boolean {
        val lobbyFolder = File(dataFolder, LOBBY_WORLD)
        if (!lobbyFolder.exists()) {
            plugin.logger.warning("Lobby folder not found: $LOBBY_WORLD")
            return false
        }
        
        // Check if there's a subfolder with level.dat (like lobby/helloween)
        val subfolders = lobbyFolder.listFiles()?.filter { 
            it.isDirectory && File(it, "level.dat").exists() 
        }
        
        if (!subfolders.isNullOrEmpty()) {
            // Load the first subfolder as the actual world
            val worldName = "${LOBBY_WORLD}/${subfolders.first().name}"
            val world = loadWorldIfExists(worldName)
            if (world != null) {
                plugin.logger.info("Loaded lobby world from subfolder: $worldName")
                return true
            }
        }
        
        // Try loading directly
        return loadWorldIfExists(LOBBY_WORLD) != null
    }
    
    /**
     * Load arena world, checking for subfolders
     */
    private fun loadArenaWorldWithSubfolder(arenaFolder: File) {
        // Check if there's a subfolder with level.dat (like arena1/helloween)
        val subfolders = arenaFolder.listFiles()?.filter { 
            it.isDirectory && File(it, "level.dat").exists() 
        }
        
        if (!subfolders.isNullOrEmpty()) {
            // Load the first subfolder as the actual world
            val worldName = "${arenaFolder.name}/${subfolders.first().name}"
            val world = loadWorldIfExists(worldName)
            if (world != null) {
                arenaWorlds[arenaFolder.name] = world
                plugin.logger.info("Loaded arena world from subfolder: $worldName")
            }
        } else {
            // Try loading directly
            loadWorldIfExists(arenaFolder.name)?.let { world ->
                arenaWorlds[arenaFolder.name] = world
            }
        }
    }
    
    /**
     * Load a world if it exists
     */
    private fun loadWorldIfExists(worldName: String): World? {
        val worldFolder = File(Bukkit.getWorldContainer(), worldName)
        
        if (!worldFolder.exists() || !worldFolder.isDirectory) {
            plugin.logger.warning("World folder not found: $worldName")
            return null
        }
        
        // Check for level.dat
        val levelDat = File(worldFolder, "level.dat")
        if (!levelDat.exists()) {
            plugin.logger.warning("Invalid world (no level.dat): $worldName")
            return null
        }
        
        return try {
            val world = Bukkit.createWorld(WorldCreator(worldName))
            if (world != null) {
                if (worldName.startsWith(ARENA_PREFIX)) {
                    arenaWorlds[worldName] = world
                }
                plugin.logger.info("Loaded world: $worldName")
            }
            world
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load world $worldName: ${e.message}")
            null
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
        // Try direct name first
        Bukkit.getWorld(LOBBY_WORLD)?.let { return it }
        
        // Try to find world with lobby in name
        return Bukkit.getWorlds().firstOrNull { it.name.contains("lobby", ignoreCase = true) }
    }
    
    /**
     * Get lobby spawn location
     */
    fun getLobbySpawn(): Location? {
        val world = getLobbyWorld() ?: return null
        return lobbySpawn ?: world.spawnLocation
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
