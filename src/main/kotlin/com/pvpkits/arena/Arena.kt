package com.pvpkits.arena

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection

/**
 * Arena data class representing a PvP arena
 */
data class Arena(
    val name: String,
    val displayName: String,
    val worldName: String,
    val spawns: List<Location>,
    val lobbySpawn: Location?,
    val minPlayers: Int = 2,
    val maxPlayers: Int = 16,
    val enabled: Boolean = true,
    val description: String = ""
) {
    /**
     * Get a random spawn point
     */
    fun getRandomSpawn(): Location? {
        return if (spawns.isNotEmpty()) spawns.random() else null
    }
    
    /**
     * Serialize to configuration section
     */
    fun saveToConfig(section: ConfigurationSection) {
        section.set("display-name", displayName)
        section.set("world", worldName)
        section.set("min-players", minPlayers)
        section.set("max-players", maxPlayers)
        section.set("enabled", enabled)
        section.set("description", description)
        
        // Save spawns
        val spawnsSection = section.createSection("spawns")
        spawns.forEachIndexed { index, loc ->
            val spawnSection = spawnsSection.createSection("spawn_$index")
            spawnSection.set("x", loc.x)
            spawnSection.set("y", loc.y)
            spawnSection.set("z", loc.z)
            spawnSection.set("yaw", loc.yaw)
            spawnSection.set("pitch", loc.pitch)
        }
        
        // Save lobby spawn
        lobbySpawn?.let { loc ->
            val lobbySection = section.createSection("lobby")
            lobbySection.set("x", loc.x)
            lobbySection.set("y", loc.y)
            lobbySection.set("z", loc.z)
            lobbySection.set("yaw", loc.yaw)
            lobbySection.set("pitch", loc.pitch)
        }
    }
    
    companion object {
        /**
         * Load from configuration section
         */
        fun loadFromConfig(name: String, section: ConfigurationSection, world: World?): Arena? {
            if (world == null) return null
            
            val displayName = section.getString("display-name") ?: name
            val minPlayers = section.getInt("min-players", 2)
            val maxPlayers = section.getInt("max-players", 16)
            val enabled = section.getBoolean("enabled", true)
            val description = section.getString("description", "")
            
            // Load spawns
            val spawnsSection = section.getConfigurationSection("spawns") ?: return null
            val spawns = mutableListOf<Location>()
            
            spawnsSection.getKeys(false).forEach { key ->
                val spawnSection = spawnsSection.getConfigurationSection(key) ?: return@forEach
                spawns.add(Location(
                    world,
                    spawnSection.getDouble("x"),
                    spawnSection.getDouble("y"),
                    spawnSection.getDouble("z"),
                    spawnSection.getDouble("yaw").toFloat(),
                    spawnSection.getDouble("pitch").toFloat()
                ))
            }
            
            // Load lobby spawn
            val lobbySection = section.getConfigurationSection("lobby")
            val lobbySpawn = lobbySection?.let {
                Location(
                    world,
                    it.getDouble("x"),
                    it.getDouble("y"),
                    it.getDouble("z"),
                    it.getDouble("yaw").toFloat(),
                    it.getDouble("pitch").toFloat()
                )
            }
            
            return Arena(
                name = name,
                displayName = displayName,
                worldName = world.name,
                spawns = spawns,
                lobbySpawn = lobbySpawn,
                minPlayers = minPlayers,
                maxPlayers = maxPlayers,
                enabled = enabled,
                description = description ?: ""
            )
        }
    }
}
