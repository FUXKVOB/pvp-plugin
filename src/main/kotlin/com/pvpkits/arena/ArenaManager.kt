package com.pvpkits.arena

import com.pvpkits.PvPKitsPlugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages arenas and player arena states
 */
class ArenaManager(private val plugin: PvPKitsPlugin) {
    
    private val arenas = ConcurrentHashMap<String, Arena>()
    private val playerArenas = ConcurrentHashMap<UUID, String>() // Player UUID -> Arena name
    private val arenaPlayers = ConcurrentHashMap<String, MutableSet<UUID>>() // Arena name -> Player UUIDs
    private val arenasFile = File(plugin.dataFolder, "arenas.yml")
    private var arenasConfig: YamlConfiguration? = null
    
    /**
     * Load all arenas from file
     */
    fun loadArenas() {
        if (!arenasFile.exists()) {
            return
        }
        
        arenasConfig = YamlConfiguration.loadConfiguration(arenasFile)
        val arenasSection = arenasConfig!!.getConfigurationSection("arenas") ?: return
        
        for (arenaName in arenasSection.getKeys(false)) {
            val arenaSection = arenasSection.getConfigurationSection(arenaName) ?: continue
            val worldName = arenaSection.getString("world") ?: continue
            val world = Bukkit.getWorld(worldName) ?: continue
            
            val arena = Arena.loadFromConfig(arenaName, arenaSection, world)
            if (arena != null) {
                arenas[arenaName.lowercase()] = arena
                arenaPlayers[arenaName.lowercase()] = mutableSetOf()
            }
        }
        
        plugin.logger.info("Loaded ${arenas.size} arenas")
    }
    
    /**
     * Save all arenas to file
     */
    fun saveArenas() {
        if (arenas.isEmpty()) return
        
        val config = YamlConfiguration()
        val arenasSection = config.createSection("arenas")
        
        arenas.values.forEach { arena ->
            val arenaSection = arenasSection.createSection(arena.name)
            arena.saveToConfig(arenaSection)
        }
        
        try {
            config.save(arenasFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save arenas: ${e.message}")
        }
    }
    
    /**
     * Create a new arena
     */
    fun createArena(
        name: String,
        displayName: String,
        worldName: String,
        spawns: List<Location>,
        lobbySpawn: Location? = null,
        minPlayers: Int = 2,
        maxPlayers: Int = 16
    ): Arena? {
        val world = Bukkit.getWorld(worldName) ?: return null
        
        val arena = Arena(
            name = name,
            displayName = displayName,
            worldName = worldName,
            spawns = spawns,
            lobbySpawn = lobbySpawn,
            minPlayers = minPlayers,
            maxPlayers = maxPlayers
        )
        
        arenas[name.lowercase()] = arena
        arenaPlayers[name.lowercase()] = mutableSetOf()
        saveArenas()
        
        return arena
    }
    
    /**
     * Delete an arena
     */
    fun deleteArena(name: String): Boolean {
        val arena = arenas.remove(name.lowercase()) ?: return false
        arenaPlayers.remove(name.lowercase())
        saveArenas()
        return true
    }
    
    /**
     * Get an arena by name
     */
    fun getArena(name: String): Arena? = arenas[name.lowercase()]
    
    /**
     * Get all arenas
     */
    fun getAllArenas(): Collection<Arena> = arenas.values
    
    /**
     * Get enabled arenas
     */
    fun getEnabledArenas(): List<Arena> = arenas.values.filter { it.enabled }
    
    /**
     * Player joins an arena
     */
    fun joinArena(player: Player, arenaName: String): Boolean {
        val arena = getArena(arenaName) ?: return false
        
        // Check if arena is enabled
        if (!arena.enabled) return false
        
        // Check if player is already in an arena
        if (playerArenas.containsKey(player.uniqueId)) {
            leaveArena(player)
        }
        
        // Check max players
        val currentPlayers = arenaPlayers[arena.name.lowercase()]?.size ?: 0
        if (currentPlayers >= arena.maxPlayers) {
            return false
        }
        
        // Add player to arena
        playerArenas[player.uniqueId] = arena.name.lowercase()
        arenaPlayers[arena.name.lowercase()]?.add(player.uniqueId)
        
        // Teleport to random spawn
        val spawn = arena.getRandomSpawn()
        if (spawn != null) {
            player.teleport(spawn)
        }
        
        // Give kit selection compass
        giveKitCompass(player)
        
        // Broadcast join
        broadcastInArena(arena.name, "§e${player.name} §7joined the arena! §f(${currentPlayers + 1}/${arena.maxPlayers})")
        
        return true
    }
    
    /**
     * Player leaves current arena
     */
    fun leaveArena(player: Player): Boolean {
        val arenaName = playerArenas.remove(player.uniqueId) ?: return false
        val arena = arenas[arenaName] ?: return false
        
        arenaPlayers[arenaName]?.remove(player.uniqueId)
        
        // Teleport to lobby or world spawn
        arena.lobbySpawn?.let { player.teleport(it) }
            ?: Bukkit.getWorld(arena.worldName)?.spawnLocation?.let { player.teleport(it) }
        
        // Clear inventory
        player.inventory.clear()
        
        // Broadcast leave
        val remaining = arenaPlayers[arenaName]?.size ?: 0
        broadcastInArena(arenaName, "§e${player.name} §7left the arena! §f($remaining/${arena.maxPlayers})")
        
        return true
    }
    
    /**
     * Get player's current arena
     */
    fun getPlayerArena(player: Player): Arena? {
        val arenaName = playerArenas[player.uniqueId] ?: return null
        return arenas[arenaName]
    }
    
    /**
     * Check if player is in an arena
     */
    fun isInArena(player: Player): Boolean = playerArenas.containsKey(player.uniqueId)
    
    /**
     * Get players in an arena
     */
    fun getPlayersInArena(arenaName: String): Set<UUID> {
        return arenaPlayers[arenaName.lowercase()] ?: emptySet()
    }
    
    /**
     * Get player count in arena
     */
    fun getPlayerCount(arenaName: String): Int {
        return arenaPlayers[arenaName.lowercase()]?.size ?: 0
    }
    
    /**
     * Respawn player in arena
     */
    fun respawnInArena(player: Player): Boolean {
        val arena = getPlayerArena(player) ?: return false
        val spawn = arena.getRandomSpawn() ?: return false
        
        player.teleport(spawn)
        giveKitCompass(player)
        return true
    }
    
    /**
     * Broadcast message to all players in an arena
     */
    fun broadcastInArena(arenaName: String, message: String) {
        val players = arenaPlayers[arenaName.lowercase()] ?: return
        players.forEach { uuid ->
            Bukkit.getPlayer(uuid)?.sendMessage(message)
        }
    }
    
    /**
     * Give kit selection compass to player
     */
    private fun giveKitCompass(player: Player) {
        val compass = org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS)
        val meta = compass.itemMeta
        meta?.setDisplayName("§6§l⚔ Kit Selection")
        meta?.lore = listOf("§7Right-click to select a kit!")
        compass.itemMeta = meta
        player.inventory.setItem(4, compass)
    }
    
    /**
     * Clean up player data
     */
    fun cleanupPlayer(uuid: UUID) {
        val arenaName = playerArenas.remove(uuid) ?: return
        arenaPlayers[arenaName]?.remove(uuid)
    }
    
    /**
     * Get memory statistics
     */
    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "arenas_loaded" to arenas.size,
            "players_in_arenas" to playerArenas.size
        )
    }
}
