package com.pvpkits

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.io.File
import java.util.*

class KitManager(private val plugin: PvPKitsPlugin) {
    private val kits = mutableMapOf<String, Kit>()
    private val cooldowns = mutableMapOf<UUID, MutableMap<String, Long>>()
    private val kitsFile: File = File(plugin.dataFolder, "kits.yml")
    private lateinit var kitsConfig: YamlConfiguration

    fun loadKits() {
        if (!kitsFile.exists()) {
            plugin.saveResource("kits.yml", false)
        }
        
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile)
        kits.clear()
        
        val kitsSection = kitsConfig.getConfigurationSection("kits") ?: return
        
        for (kitName in kitsSection.getKeys(false)) {
            val kitSection = kitsSection.getConfigurationSection(kitName) ?: continue
            
            val displayName = kitSection.getString("display-name") ?: kitName
            val icon = kitSection.getString("icon")
            val permission = kitSection.getString("permission")
            val cooldown = kitSection.getInt("cooldown", 60)
            val description = kitSection.getStringList("description")
            val itemStrings = kitSection.getStringList("items")
            
            val items = itemStrings.mapNotNull { parseItemString(it) }
            
            kits[kitName.lowercase()] = Kit(kitName, displayName, icon, permission, cooldown, description, items)
        }
        
        plugin.logger.info("Loaded ${kits.size} kits")
    }

    private fun parseItemString(itemString: String): ItemStack? {
        try {
            val parts = itemString.split(" ", limit = 2)
            val materialAndNbt = parts[0]
            val amount = if (parts.size > 1) parts[1].toIntOrNull() ?: 1 else 1
            
            val nbtStart = materialAndNbt.indexOf('{')
            val materialName = if (nbtStart > 0) materialAndNbt.substring(0, nbtStart) else materialAndNbt
            
            val material = Material.getMaterial(materialName.uppercase()) ?: return null
            val item = ItemStack(material, amount)
            
            if (nbtStart > 0) {
                val nbtString = materialAndNbt.substring(nbtStart)
                applyNBT(item, nbtString)
            }
            
            return item
        } catch (e: Exception) {
            plugin.logger.warning("Failed to parse item: $itemString - ${e.message}")
            return null
        }
    }

    private fun applyNBT(item: ItemStack, nbtString: String) {
        val meta = item.itemMeta ?: return
        
        if (nbtString.contains("Enchantments:")) {
            val enchantPattern = "id:([a-z_]+),lvl:(\\d+)".toRegex()
            enchantPattern.findAll(nbtString).forEach { match ->
                val enchantName = match.groupValues[1]
                val level = match.groupValues[2].toIntOrNull() ?: 1
                
                val enchant = Enchantment.getByName(enchantName.uppercase()) 
                    ?: Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchantName))
                
                if (enchant != null) {
                    meta.addEnchant(enchant, level, true)
                }
            }
        }
        
        item.itemMeta = meta
    }

    fun getKit(name: String): Kit? = kits[name.lowercase()]

    fun getAllKits(): Collection<Kit> = kits.values

    fun giveKit(player: org.bukkit.entity.Player, kitName: String): Boolean {
        val kit = getKit(kitName) ?: return false
        
        if (kit.permission != null && !player.hasPermission(kit.permission)) {
            return false
        }
        
        // Убрали проверку кулдауна для быстрого выбора
        
        if (plugin.config.getBoolean("clear-inventory", true)) {
            player.inventory.clear()
        }
        
        kit.items.forEach { item ->
            player.inventory.addItem(item.clone())
        }
        
        // Даём эффекты скорости
        player.addPotionEffect(org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.SPEED, 
            999999, 
            1, 
            false, 
            false
        ))
        
        // Record kit usage in stats
        if (plugin.config.getBoolean("stats.enabled", true)) {
            plugin.statsManager.recordKitUse(player.uniqueId, player.name, kit.name)
        }
        
        return true
    }

    fun hasCooldown(uuid: UUID, kitName: String): Boolean {
        val playerCooldowns = cooldowns[uuid] ?: return false
        val cooldownEnd = playerCooldowns[kitName.lowercase()] ?: return false
        return System.currentTimeMillis() < cooldownEnd
    }

    fun getCooldownRemaining(uuid: UUID, kitName: String): Long {
        val playerCooldowns = cooldowns[uuid] ?: return 0
        val cooldownEnd = playerCooldowns[kitName.lowercase()] ?: return 0
        val remaining = cooldownEnd - System.currentTimeMillis()
        return if (remaining > 0) remaining / 1000 else 0
    }

    private fun setCooldown(uuid: UUID, kitName: String, seconds: Int) {
        if (seconds <= 0) return
        
        val playerCooldowns = cooldowns.getOrPut(uuid) { mutableMapOf() }
        playerCooldowns[kitName.lowercase()] = System.currentTimeMillis() + (seconds * 1000L)
    }

    fun createKit(name: String, displayName: String, icon: String?, permission: String?, cooldown: Int, description: List<String>, items: List<ItemStack>) {
        kits[name.lowercase()] = Kit(name, displayName, icon, permission, cooldown, description, items)
        saveKit(name, displayName, icon, permission, cooldown, description, items)
    }

    private fun saveKit(name: String, displayName: String, icon: String?, permission: String?, cooldown: Int, description: List<String>, items: List<ItemStack>) {
        val kitPath = "kits.$name"
        kitsConfig.set("$kitPath.display-name", displayName)
        kitsConfig.set("$kitPath.icon", icon)
        kitsConfig.set("$kitPath.permission", permission)
        kitsConfig.set("$kitPath.cooldown", cooldown)
        kitsConfig.set("$kitPath.description", description)
        
        val itemStrings = items.map { itemToString(it) }
        kitsConfig.set("$kitPath.items", itemStrings)
        
        try {
            kitsConfig.save(kitsFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save kit: ${e.message}")
        }
    }

    private fun itemToString(item: ItemStack): String {
        val builder = StringBuilder(item.type.name)
        
        if (item.itemMeta?.hasEnchants() == true) {
            builder.append("{Enchantments:[")
            val enchants = item.enchantments.entries.joinToString(",") { (ench, lvl) ->
                "{id:${ench.key.key},lvl:$lvl}"
            }
            builder.append(enchants).append("]}")
        }
        
        if (item.amount > 1) {
            builder.append(" ${item.amount}")
        }
        
        return builder.toString()
    }

    fun deleteKit(name: String): Boolean {
        if (kits.remove(name.lowercase()) != null) {
            kitsConfig.set("kits.$name", null)
            try {
                kitsConfig.save(kitsFile)
                return true
            } catch (e: Exception) {
                plugin.logger.severe("Failed to delete kit: ${e.message}")
            }
        }
        return false
    }
    
    /**
     * Clean up player data to prevent memory leaks
     * Call this when player quits the server
     */
    fun cleanupPlayer(uuid: UUID) {
        cooldowns.remove(uuid)
    }
    
    /**
     * Get memory usage statistics for monitoring
     */
    fun getMemoryStats(): Map<String, Any> {
        return mapOf(
            "kits_loaded" to kits.size,
            "players_with_cooldowns" to cooldowns.size,
            "total_cooldown_entries" to cooldowns.values.sumOf { it.size }
        )
    }
}
