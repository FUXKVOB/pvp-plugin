package com.pvpkits.arena

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.SchedulerUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ImprovedArenaManager(private val plugin: PvPKitsPlugin) {

    private val templates = ConcurrentHashMap<String, ArenaTemplate>()
    private val instances = ConcurrentHashMap<String, ArenaInstance>()
    private val playerInstances = ConcurrentHashMap<UUID, String>()
    private val templatesFile = File(plugin.dataFolder, "arena-templates.yml")
    private val arenaBlocks = ConcurrentHashMap<String, MutableMap<Location, Material>>()

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

    private fun createDefaultTemplates() {
        val config = YamlConfiguration()
        val templatesSection = config.createSection("templates")
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

    fun getAvailableInstance(kitName: String? = null): ArenaInstance? {
        val freeInstance = instances.values.find {
            !it.inUse && it.template.enabled && (kitName == null || it.template.isKitAllowed(kitName))
        }
        if (freeInstance != null) {
            return freeInstance
        }

        val availableTemplate = templates.values.find {
            it.enabled && (kitName == null || it.isKitAllowed(kitName))
        } ?: return null

        return createInstance(availableTemplate)
    }

    private fun createInstance(template: ArenaTemplate): ArenaInstance {
        val instanceId = "${template.name}_${UUID.randomUUID().toString().substring(0, 8)}"
        val instance = ArenaInstance(template = template, instanceId = instanceId)
        instances[instanceId] = instance
        arenaBlocks[instanceId] = mutableMapOf()
        plugin.logger.info("Created arena instance: $instanceId from template ${template.name}")
        return instance
    }

    fun startMatch(instance: ArenaInstance, player1: Player, player2: Player): Boolean {
        if (instance.inUse) return false

        instance.markInUse()
        playerInstances[player1.uniqueId] = instance.instanceId
        playerInstances[player2.uniqueId] = instance.instanceId

        player1.teleport(instance.template.spawn1)
        player2.teleport(instance.template.spawn2)

        preparePlayer(player1)
        preparePlayer(player2)
        startBlockTracking(instance)

        plugin.logger.info("Started match in ${instance.instanceId}: ${player1.name} vs ${player2.name}")
        return true
    }

    fun endMatch(instance: ArenaInstance) {
        instance.markFree()
        playerInstances.entries.removeIf { it.value == instance.instanceId }
        resetArena(instance)
        plugin.logger.info("Ended match in ${instance.instanceId}")
    }

    private fun startBlockTracking(instance: ArenaInstance) {
        val blocks = arenaBlocks[instance.instanceId] ?: return
        blocks.clear()

        val template = instance.template
        val world = Bukkit.getWorld(template.worldName) ?: return
        val minX = template.minBounds.blockX
        val minY = template.minBounds.blockY
        val minZ = template.minBounds.blockZ
        val maxX = template.maxBounds.blockX
        val maxY = template.maxBounds.blockY
        val maxZ = template.maxBounds.blockZ

        processArenaBlocks(world, minX, minY, minZ, maxX, maxY, maxZ, 250, { location ->
            blocks[location] = world.getBlockAt(location).type
        }) {
            plugin.logger.info("Saved ${blocks.size} blocks for ${instance.instanceId}")
        }
    }

    private fun resetArena(instance: ArenaInstance) {
        val blocks = arenaBlocks[instance.instanceId] ?: return
        val entries = blocks.entries.toList()
        var index = 0
        var restored = 0
        val batchSize = 250

        val taskRef = arrayOfNulls<org.bukkit.scheduler.BukkitTask>(1)
        taskRef[0] = SchedulerUtils.runTaskTimer(plugin, 0L, 1L, Runnable {
            val endIndex = (index + batchSize).coerceAtMost(entries.size)
            while (index < endIndex) {
                val (loc, material) = entries[index]
                val block = loc.block
                if (block.type != material) {
                    block.type = material
                    restored++
                }
                index++
            }

            if (index < entries.size) {
                return@Runnable
            }

            blocks.clear()
            if (instance.needsReset()) {
                instance.reset()
            }
            plugin.logger.info("Reset ${instance.instanceId}: restored $restored blocks")
            taskRef[0]?.cancel()
        })
    }

    private fun processArenaBlocks(
        world: World,
        minX: Int,
        minY: Int,
        minZ: Int,
        maxX: Int,
        maxY: Int,
        maxZ: Int,
        batchSize: Int,
        consumer: (Location) -> Unit,
        onComplete: () -> Unit
    ) {
        val locations = ArrayList<Location>((maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1))
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    locations.add(Location(world, x.toDouble(), y.toDouble(), z.toDouble()))
                }
            }
        }

        var index = 0
        val taskRef = arrayOfNulls<org.bukkit.scheduler.BukkitTask>(1)
        taskRef[0] = SchedulerUtils.runTaskTimer(plugin, 0L, 1L, Runnable {
            val endIndex = (index + batchSize).coerceAtMost(locations.size)
            while (index < endIndex) {
                consumer(locations[index])
                index++
            }

            if (index < locations.size) {
                return@Runnable
            }

            onComplete()
            taskRef[0]?.cancel()
        })
    }

    private fun preparePlayer(player: Player) {
        player.health = player.maxHealth
        player.foodLevel = 20
        player.saturation = 20f
        player.fireTicks = 0
        player.fallDistance = 0f
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
    }

    fun getPlayerInstance(player: Player): ArenaInstance? {
        val instanceId = playerInstances[player.uniqueId] ?: return null
        return instances[instanceId]
    }

    fun isInArena(player: Player): Boolean = playerInstances.containsKey(player.uniqueId)

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

    fun getAllTemplates(): Collection<ArenaTemplate> = templates.values

    fun getTemplate(name: String): ArenaTemplate? = templates[name.lowercase()]

    fun deleteTemplate(name: String): Boolean {
        val template = templates.remove(name.lowercase()) ?: return false
        instances.values.removeIf { it.template.name == name }
        saveTemplates()
        return true
    }

    fun cleanupPlayer(uuid: UUID) {
        val instanceId = playerInstances.remove(uuid) ?: return
        val instance = instances[instanceId] ?: return
        val remainingPlayers = playerInstances.values.count { it == instanceId }
        if (remainingPlayers == 0) {
            endMatch(instance)
        }
    }

    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "templates" to templates.size,
            "active_instances" to instances.values.count { it.inUse },
            "total_instances" to instances.size,
            "players_in_arenas" to playerInstances.size
        )
    }
}
