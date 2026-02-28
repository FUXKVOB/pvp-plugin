package com.pvpkits.arena

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.CoroutineUtils
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Improved Arena Manager with proper map handling
 * 
 * Features:
 * - Arena templates (master copies)
 * - Arena instances (active matches)
 * - Automatic reset after matches
 * - Block restoration
 * - Multiple instances per template
 */
class ImprovedArenaManager(private val plugin: PvPKitsPlugin) {
    
    private val templates = ConcurrentHashMap<String, ArenaTemplate>()
    private val instances = ConcurrentHashMap<String, ArenaInstance>()
    private val playerInstances = ConcurrentHashMap<UUID, String>() // player -> instance ID
    
    private val templatesFile = File(plugin.dataFolder, "arena-templates.yml")
    
    // Block tracking for restoration
    private val arenaBlocks = ConcurrentHashMap<String, MutableMap<Location, Material>>()
    
    /**
     * Load arena templates
     */
    fun loadTemplates() {
        if (!templatesFile.exists()) {
            createDefaultTemplates()
            return
        }
        
        val config = YamlConfiguration.loadConfiguration(templatesFile)
        val templatesSection = config.getConfigurationSection("templates") ?: return
        
        templatesSection.getKeys(false).forEach { name ->
            val section = templatesSection.getConfigurationSection(name) ?: return@forEach
            val worldName = section.getString("world") ?: return@forEach
            val world = Bukkit.getWorld(worldName) ?: return@forEach
            
            ArenaTemplate.loadFromConfig(name, section, world)?.let { template ->
                templates[name.lowercase()] = template
                plugin.logger.info("Loaded arena template: $name")
            }
        }
        
        plugin.logger.info("Loaded ${templates.size} arena templates")
    }
    
    /**
     * Create default templates from existing arenas
     */
    private fun createDefaultTemplates() {
        val config = YamlConfiguration()
        val templatesSection = config.createSection("templates")
        
        // Create example template
        val exampleSection = templatesSection.createSection("example")
        exampleSection.set("display-name", "Example Arena")
        exampleSection.set("world", "world")
        exampleSection.set("enabled", false)
        exampleSection.set("spawn1.x", 0.0)
        exampleSection.set("spawn1.y", 64.0)
        exampleSection.set("spawn1.z", 10.0)
        exampleSection.set("spawn2.x", 0.0)
        exampleSection.set("spawn2.y", 64.0)
        exampleSection.set("spawn2.z", -10.0)
        exampleSection.set("bounds.min.x", -20)
        exampleSection.set("bounds.min.y", 60)
        exampleSection.set("bounds.min.z", -20)
        exampleSection.set("bounds.max.x", 20)
        exampleSection.set("bounds.max.y", 80)
        exampleSection.set("bounds.max.z", 20)
        
        try {
            config.save(templatesFile)
            plugin.logger.info("Created default arena templates file")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to create templates file: ${e.message}")
        }
    }
    
    /**
     * Save templates
     */
    fun saveTemplates() {
        val config = YamlConfiguration()
        val templatesSection = config.createSection("templates")
        
        templates.values.forEach { template ->
            val section = templatesSection.createSection(template.name)
            template.saveToConfig(section)
        }
        
        try {
            config.save(templatesFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save templates: ${e.message}")
        }
    }
    
    /**
     * Get available arena instance for match
     */
    fun getAvailableInstance(kitName: String? = null): ArenaInstance? {
        // Find free instance
        val freeInstance = instances.values.find { 
            !it.inUse && 
            it.template.enabled &&
            (kitName == null || it.template.isKitAllowed(kitName))
        }
        
        if (freeInstance != null) {
            return freeInstance
        }
        
        // Create new instance from template
        val availableTemplate = templates.values.find { 
            it.enabled && 
            (kitName == null || it.isKitAllowed(kitName))
        } ?: return null
        
        return createInstance(availableTemplate)
    }
    
    /**
     * Create new arena instance
     */
    private fun createInstance(template: ArenaTemplate): ArenaInstance {
        val instanceId = "${template.name}_${UUID.randomUUID().toString().substring(0, 8)}"
        
        val instance = ArenaInstance(
            template = template,
            instanceId = instanceId
        )
        
        instances[instanceId] = instance
        arenaBlocks[instanceId] = mutableMapOf()
        
        plugin.logger.info("Created arena instance: $instanceId from template ${template.name}")
        
        return instance
    }
    
    /**
     * Start match in instance
     */
    fun startMatch(instance: ArenaInstance, player1: Player, player2: Player): Boolean {
        if (instance.inUse) return false
        
        instance.markInUse()
        playerInstances[player1.uniqueId] = instance.instanceId
        playerInstances[player2.uniqueId] = instance.instanceId
        
        // Teleport players
        player1.teleport(instance.template.spawn1)
        player2.teleport(instance.template.spawn2)
        
        // Prepare players
        preparePlayer(player1)
        preparePlayer(player2)
        
        // Start tracking blocks
        startBlockTracking(instance)
        
        plugin.logger.info("Started match in ${instance.instanceId}: ${player1.name} vs ${player2.name}")
        
        return true
    }
    
    /**
     * End match and reset arena
     */
    fun endMatch(instance: ArenaInstance) {
        instance.markFree()
        
        // Remove player mappings
        playerInstances.entries.removeIf { it.value == instance.instanceId }
        
        // Reset arena blocks
        plugin.launch {
            resetArena(instance)
        }
        
        plugin.logger.info("Ended match in ${instance.instanceId}")
    }
    
    /**
     * Start tracking block changes in arena
     */
    private fun startBlockTracking(instance: ArenaInstance) {
        val blocks = arenaBlocks[instance.instanceId] ?: return
        blocks.clear()
        
        // Save original blocks in bounds
        val template = instance.template
        val world = Bukkit.getWorld(template.worldName) ?: return
        
        val minX = template.minBounds.blockX
        val minY = template.minBounds.blockY
        val minZ = template.minBounds.blockZ
        val maxX = template.maxBounds.blockX
        val maxY = template.maxBounds.blockY
        val maxZ = template.maxBounds.blockZ
        
        // Save blocks (async to avoid lag)
        plugin.launch {
            CoroutineUtils.io {
                for (x in minX..maxX) {
                    for (y in minY..maxY) {
                        for (z in minZ..maxZ) {
                            val loc = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                            val block = world.getBlockAt(loc)
                            blocks[loc] = block.type
                        }
                    }
                }
                plugin.logger.info("Saved ${blocks.size} blocks for ${instance.instanceId}")
            }
        }
    }
    
    /**
     * Reset arena to original state
     */
    private suspend fun resetArena(instance: ArenaInstance) {
        val blocks = arenaBlocks[instance.instanceId] ?: return
        
        CoroutineUtils.io {
            var restored = 0
            blocks.forEach { (loc, material) ->
                val block = loc.block
                if (block.type != material) {
                    block.type = material
                    restored++
                }
            }
            
            plugin.logger.info("Reset ${instance.instanceId}: restored $restored blocks")
            
            // Clear tracking
            blocks.clear()
            
            // Reset instance counter if needed
            if (instance.needsReset()) {
                instance.reset()
            }
        }
    }
    
    /**
     * Prepare player for match
     */
    private fun preparePlayer(player: Player) {
        player.health = player.maxHealth
        player.foodLevel = 20
        player.saturation = 20f
        player.fireTicks = 0
        player.fallDistance = 0f
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
    }
    
    /**
     * Get player's current instance
     */
    fun getPlayerInstance(player: Player): ArenaInstance? {
        val instanceId = playerInstances[player.uniqueId] ?: return null
        return instances[instanceId]
    }
    
    /**
     * Check if player is in arena
     */
    fun isInArena(player: Player): Boolean {
        return playerInstances.containsKey(player.uniqueId)
    }
    
    /**
     * Create arena template
     */
    fun createTemplate(
        name: String,
        displayName: String,
        world: World,
        spawn1: Location,
        spawn2: Location,
        minBounds: Location,
        maxBounds: Location
    ): ArenaTemplate {
        val template = ArenaTemplate(
            name = name,
            displayName = displayName,
            worldName = world.name,
            spawn1 = spawn1,
            spawn2 = spawn2,
            minBounds = minBounds,
            maxBounds = maxBounds
        )
        
        templates[name.lowercase()] = template
        saveTemplates()
        
        plugin.logger.info("Created arena template: $name")
        
        return template
    }
    
    /**
     * Get all templates
     */
    fun getAllTemplates(): Collection<ArenaTemplate> = templates.values
    
    /**
     * Get template by name
     */
    fun getTemplate(name: String): ArenaTemplate? = templates[name.lowercase()]
    
    /**
     * Delete template
     */
    fun deleteTemplate(name: String): Boolean {
        val template = templates.remove(name.lowercase()) ?: return false
        
        // Remove all instances of this template
        instances.values.removeIf { it.template.name == name }
        
        saveTemplates()
        return true
    }
    
    /**
     * Cleanup player
     */
    fun cleanupPlayer(uuid: UUID) {
        val instanceId = playerInstances.remove(uuid) ?: return
        val instance = instances[instanceId] ?: return
        
        // If no more players, end match
        val remainingPlayers = playerInstances.values.count { it == instanceId }
        if (remainingPlayers == 0) {
            endMatch(instance)
        }
    }
    
    /**
     * Get memory stats
     */
    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "templates" to templates.size,
            "active_instances" to instances.values.count { it.inUse },
            "total_instances" to instances.size,
            "players_in_arenas" to playerInstances.size
        )
    }
}
