package com.pvpkits.arena

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ArenaManager(private val plugin: PvPKitsPlugin) {

    private val arenas = ConcurrentHashMap<String, Arena>()
    private val playerArenas = ConcurrentHashMap<UUID, String>()
    private val arenaPlayers = ConcurrentHashMap<String, MutableSet<UUID>>()
    private val arenasFile = File(plugin.dataFolder, "arenas.yml")
    private var arenasConfig: YamlConfiguration? = null

    fun loadArenas() {
        if (!arenasFile.exists()) return

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

    fun createArena(
        name: String,
        displayName: String,
        worldName: String,
        spawns: List<Location>,
        lobbySpawn: Location? = null,
        minPlayers: Int = 2,
        maxPlayers: Int = 16
    ): Arena? {
        Bukkit.getWorld(worldName) ?: return null

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

    fun deleteArena(name: String): Boolean {
        val removed = arenas.remove(name.lowercase()) ?: return false
        arenaPlayers.remove(removed.name.lowercase())
        saveArenas()
        return true
    }

    fun getArena(name: String): Arena? = arenas[name.lowercase()]

    fun getAllArenas(): Collection<Arena> = arenas.values

    fun getEnabledArenas(): List<Arena> = arenas.values.filter { it.enabled }

    fun joinArena(player: Player, arenaName: String): Boolean {
        val arena = getArena(arenaName) ?: return false
        if (!arena.enabled) return false

        if (playerArenas.containsKey(player.uniqueId)) {
            leaveArena(player)
        }

        val currentPlayers = arenaPlayers[arena.name.lowercase()]?.size ?: 0
        if (currentPlayers >= arena.maxPlayers) return false

        playerArenas[player.uniqueId] = arena.name.lowercase()
        arenaPlayers[arena.name.lowercase()]?.add(player.uniqueId)

        arena.getRandomSpawn()?.let(player::teleport)
        giveKitCompass(player)
        broadcastInArena(
            arena.name,
            TextUtils.legacySection("<yellow>${player.name}</yellow> <gray>joined the arena! <white>(${currentPlayers + 1}/${arena.maxPlayers})")
        )
        return true
    }

    fun leaveArena(player: Player): Boolean {
        val arenaName = playerArenas.remove(player.uniqueId) ?: return false
        val arena = arenas[arenaName] ?: return false

        arenaPlayers[arenaName]?.remove(player.uniqueId)
        arena.lobbySpawn?.let(player::teleport)
            ?: Bukkit.getWorld(arena.worldName)?.spawnLocation?.let(player::teleport)

        player.inventory.clear()

        val remaining = arenaPlayers[arenaName]?.size ?: 0
        broadcastInArena(
            arenaName,
            TextUtils.legacySection("<yellow>${player.name}</yellow> <gray>left the arena! <white>($remaining/${arena.maxPlayers})")
        )
        return true
    }

    fun getPlayerArena(player: Player): Arena? = playerArenas[player.uniqueId]?.let(arenas::get)

    fun isInArena(player: Player): Boolean = playerArenas.containsKey(player.uniqueId)

    fun getPlayersInArena(arenaName: String): Set<UUID> = arenaPlayers[arenaName.lowercase()] ?: emptySet()

    fun getPlayerCount(arenaName: String): Int = arenaPlayers[arenaName.lowercase()]?.size ?: 0

    fun respawnInArena(player: Player): Boolean {
        val arena = getPlayerArena(player) ?: return false
        val spawn = arena.getRandomSpawn() ?: return false
        player.teleport(spawn)
        giveKitCompass(player)
        return true
    }

    fun broadcastInArena(arenaName: String, message: String) {
        val players = arenaPlayers[arenaName.lowercase()] ?: return
        players.forEach { uuid -> Bukkit.getPlayer(uuid)?.sendMessage(message) }
    }

    private fun giveKitCompass(player: Player) {
        val compass = org.bukkit.inventory.ItemStack(org.bukkit.Material.COMPASS)
        val meta = compass.itemMeta
        meta?.displayName(TextUtils.parseAuto("<gold><bold>Kit Selection"))
        meta?.lore(TextUtils.lines("<gray>Right-click to select a kit!"))
        meta?.persistentDataContainer?.set(plugin.itemKeys.kitCompass, PersistentDataType.BYTE, 1)
        compass.itemMeta = meta
        player.inventory.setItem(4, compass)
    }

    fun cleanupPlayer(uuid: UUID) {
        val arenaName = playerArenas.remove(uuid) ?: return
        arenaPlayers[arenaName]?.remove(uuid)
    }

    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "arenas_loaded" to arenas.size,
            "players_in_arenas" to playerArenas.size
        )
    }
}
