package com.pvpkits.gui

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.pvpkits.Kit
import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.ComponentCache
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * Kit GUI with advanced caching using Caffeine
 * Best Practices 2026:
 * - Lazy item creation (only for current page)
 * - Item caching with Caffeine
 * - Batch operations for better performance
 * - MiniMessage component caching
 */
class KitGUI(private val plugin: PvPKitsPlugin) {
    
    private val playerPages = mutableMapOf<Player, Int>()
    
    // Caffeine cache for kit icons - high performance with automatic expiration
    private val kitIconCache: Cache<String, ItemStack> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .recordStats()
        .build()
    
    companion object {
        private const val CACHE_EXPIRATION_MINUTES = 5L
    }
    
    fun openKitMenu(player: Player, page: Int = 1) {
        val kits = plugin.kitManager.getAllKits().toList()
        val itemsPerPage = plugin.config.getInt("gui.items-per-page", 28)
        val totalPages = ceil(kits.size.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        val currentPage = page.coerceIn(1, totalPages)
        
        playerPages[player] = currentPage
        
        val rows = plugin.config.getInt("gui.rows", 6)
        val title = translateColors(plugin.config.getString("gui.title") ?: "⚔ PvP Kits")
        val inventory = Bukkit.createInventory(null, rows * 9, title)
        
        // Fill borders with glass panes
        fillBorders(inventory, rows)
        
        // Add kits for current page
        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = (startIndex + itemsPerPage).coerceAtMost(kits.size)
        val pageKits = kits.subList(startIndex, endIndex)
        
        val kitSlots = getKitSlots(rows)
        pageKits.forEachIndexed { index, kit ->
            if (index < kitSlots.size) {
                inventory.setItem(kitSlots[index], createKitIcon(kit, player))
            }
        }
        
        // Navigation buttons
        if (currentPage > 1) {
            inventory.setItem(rows * 9 - 9, createPreviousPageButton())
        }
        
        if (currentPage < totalPages) {
            inventory.setItem(rows * 9 - 1, createNextPageButton())
        }
        
        // Info button
        inventory.setItem(rows * 9 - 5, createInfoButton(currentPage, totalPages, kits.size))
        
        player.openInventory(inventory)
        playSound(player, "gui-click")
    }
    
    private fun fillBorders(inventory: Inventory, rows: Int) {
        val borderPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            val meta = itemMeta!!
            meta.setDisplayName(" ")
            itemMeta = meta
        }
        
        val accentPane = ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
            val meta = itemMeta!!
            meta.setDisplayName(" ")
            itemMeta = meta
        }
        
        // Top and bottom rows
        for (i in 0 until 9) {
            inventory.setItem(i, if (i % 2 == 0) accentPane else borderPane)
            inventory.setItem((rows - 1) * 9 + i, if (i % 2 == 0) accentPane else borderPane)
        }
        
        // Side columns
        for (row in 1 until rows - 1) {
            inventory.setItem(row * 9, borderPane)
            inventory.setItem(row * 9 + 8, borderPane)
        }
    }
    
    private fun getKitSlots(rows: Int): List<Int> {
        val slots = mutableListOf<Int>()
        for (row in 1 until rows - 1) {
            for (col in 1..7) {
                slots.add(row * 9 + col)
            }
        }
        return slots
    }
    
    private fun createKitIcon(kit: Kit, player: Player): ItemStack {
        // Create cache key based on player permissions and cooldown status
        val cacheKey = buildCacheKey(kit.name, player.uniqueId, kit.permission, 
            plugin.kitManager.hasCooldown(player.uniqueId, kit.name),
            plugin.kitManager.getCooldownRemaining(player.uniqueId, kit.name) / 10)
        
        // Return cached icon if available (Caffeine handles expiration automatically)
        kitIconCache.getIfPresent(cacheKey)?.let { return it.clone() }
        
        val iconMaterial = kit.icon?.let { Material.getMaterial(it) } 
            ?: kit.items.firstOrNull()?.type 
            ?: Material.CHEST
        
        val item = ItemStack(iconMaterial)
        val meta = item.itemMeta!!
        
        meta.setDisplayName(translateColors(kit.displayName))
        
        val lore = mutableListOf<String>()
        
        // Add description
        kit.description.forEach { line ->
            lore.add(translateColors(line))
        }
        
        lore.add("")
        lore.add("${ChatColor.GRAY}━━━━━━━━━━━━━━━━━━━━")
        lore.add("${ChatColor.YELLOW}📦 Items: ${ChatColor.WHITE}${kit.items.size}")
        lore.add("${ChatColor.YELLOW}⏱ Cooldown: ${ChatColor.WHITE}${formatTime(kit.cooldown.toLong())}")
        
        // Permission check
        if (kit.permission != null && !player.hasPermission(kit.permission)) {
            lore.add("")
            lore.add("${ChatColor.RED}✗ ${ChatColor.GRAY}No Permission")
            lore.add("${ChatColor.DARK_GRAY}Required: ${ChatColor.GRAY}${kit.permission}")
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true)
        } else if (plugin.kitManager.hasCooldown(player.uniqueId, kit.name)) {
            val remaining = plugin.kitManager.getCooldownRemaining(player.uniqueId, kit.name)
            lore.add("")
            lore.add("${ChatColor.RED}⏱ ${ChatColor.GRAY}Cooldown: ${ChatColor.RED}${formatTime(remaining)}")
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true)
        } else {
            lore.add("")
            lore.add("${ChatColor.GREEN}✓ ${ChatColor.GRAY}Click to claim!")
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true)
        }
        
        lore.add("${ChatColor.GRAY}━━━━━━━━━━━━━━━━━━━━")
        
        meta.lore = lore
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta
        
        // Cache the icon using Caffeine
        kitIconCache.put(cacheKey, item.clone())
        
        return item
    }
    
    private fun buildCacheKey(kitName: String, uuid: java.util.UUID, permission: String?, hasCooldown: Boolean, cooldownBucket: Long): String {
        val permKey = if (permission != null) "perm_$permission" else "no_perm"
        val cooldownKey = if (hasCooldown) "cd_$cooldownBucket" else "no_cd"
        return "${kitName}_${uuid}_${permKey}_${cooldownKey}"
    }
    
    fun invalidateKitCache(kitName: String) {
        // Caffeine doesn't support partial invalidation by key pattern
        // Invalidate all and let it rebuild
        kitIconCache.invalidateAll()
    }
    
    fun clearAllCache() {
        kitIconCache.invalidateAll()
    }
    
    /**
     * Get cache statistics for monitoring
     * Includes both item cache and component cache stats
     */
    fun getCacheStats(): Map<String, Any> {
        val itemStats = kitIconCache.stats()
        val componentStats = ComponentCache.getCacheStats()
        
        return mapOf(
            "item_cache_size" to kitIconCache.estimatedSize(),
            "item_hit_rate" to itemStats.hitRate(),
            "item_miss_rate" to itemStats.missRate(),
            "item_eviction_count" to itemStats.evictionCount(),
            "component_cache_stats" to componentStats
        )
    }
    
    /**
     * Get formatted cache statistics string
     */
    fun getCacheStatsFormatted(): String {
        val stats = kitIconCache.stats()
        return "Item Cache - Hits: ${stats.hitCount()}, Misses: ${stats.missCount()}, Hit Rate: ${String.format("%.2f", stats.hitRate() * 100)}%"
    }
    
    private fun createPreviousPageButton(): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta!!
        meta.setDisplayName("${ChatColor.YELLOW}← ${ChatColor.GOLD}Previous Page")
        meta.lore = listOf(
            "",
            "${ChatColor.GRAY}Click to go back"
        )
        item.itemMeta = meta
        return item
    }
    
    private fun createNextPageButton(): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta!!
        meta.setDisplayName("${ChatColor.YELLOW}Next Page ${ChatColor.GOLD}→")
        meta.lore = listOf(
            "",
            "${ChatColor.GRAY}Click to continue"
        )
        item.itemMeta = meta
        return item
    }
    
    private fun createInfoButton(currentPage: Int, totalPages: Int, totalKits: Int): ItemStack {
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta!!
        meta.setDisplayName("${ChatColor.AQUA}ℹ ${ChatColor.BOLD}Information")
        meta.lore = listOf(
            "",
            "${ChatColor.GRAY}Page: ${ChatColor.WHITE}$currentPage${ChatColor.GRAY}/${ChatColor.WHITE}$totalPages",
            "${ChatColor.GRAY}Total Kits: ${ChatColor.WHITE}$totalKits",
            "",
            "${ChatColor.YELLOW}⚡ ${ChatColor.GRAY}Choose your kit wisely!"
        )
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        item.itemMeta = meta
        return item
    }
    
    fun handleClick(player: Player, slot: Int, clickedItem: ItemStack?) {
        clickedItem ?: return
        
        when (clickedItem.type) {
            Material.ARROW -> {
                val displayName = clickedItem.itemMeta?.displayName ?: ""
                val currentPage = playerPages[player] ?: 1
                
                if (displayName.contains("Previous")) {
                    openKitMenu(player, currentPage - 1)
                    playSound(player, "page-turn")
                } else if (displayName.contains("Next")) {
                    openKitMenu(player, currentPage + 1)
                    playSound(player, "page-turn")
                }
            }
            Material.GRAY_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE, Material.BOOK -> {
                // Decorative items, do nothing
            }
            else -> {
                // Kit selection
                val displayName = clickedItem.itemMeta?.displayName ?: return
                val kits = plugin.kitManager.getAllKits()
                val kit = kits.find { 
                    translateColors(it.displayName) == displayName 
                } ?: return
                
                player.closeInventory()
                
                if (kit.permission != null && !player.hasPermission(kit.permission)) {
                    player.sendMessage(getMessage("no-permission"))
                    playSound(player, "kit-cooldown")
                    return
                }
                
                if (plugin.kitManager.hasCooldown(player.uniqueId, kit.name)) {
                    val remaining = plugin.kitManager.getCooldownRemaining(player.uniqueId, kit.name)
                    player.sendMessage(getMessage("kit-cooldown").replace("{time}", formatTime(remaining)))
                    playSound(player, "kit-cooldown")
                    return
                }
                
                if (plugin.kitManager.giveKit(player, kit.name)) {
                    player.sendMessage(getMessage("kit-received").replace("{kit}", translateColors(kit.displayName)))
                    playSound(player, "kit-select")
                }
            }
        }
    }
    
    private fun translateColors(text: String): String {
        var result = ChatColor.translateAlternateColorCodes('&', text)
        
        // Simple gradient support
        val gradientPattern = "<gradient:(#[0-9a-fA-F]{6}):(#[0-9a-fA-F]{6})>(.+?)</gradient>".toRegex()
        result = gradientPattern.replace(result) { matchResult ->
            matchResult.groupValues[3] // Just return the text without gradient for now
        }
        
        return result
    }
    
    private fun getMessage(key: String): String {
        val prefix = plugin.config.getString("messages.prefix") ?: ""
        val message = plugin.config.getString("messages.$key") ?: key
        return translateColors(prefix + message)
    }
    
    private fun formatTime(seconds: Long): String {
        return when {
            seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }
    
    private fun playSound(player: Player, soundKey: String) {
        if (!plugin.config.getBoolean("gui.enable-sounds", true)) return
        
        val soundName = plugin.config.getString("sounds.$soundKey") ?: return
        try {
            val sound = Sound.valueOf(soundName)
            player.playSound(player.location, sound, 1.0f, 1.0f)
        } catch (e: Exception) {
            // Invalid sound name
        }
    }
    
    fun cleanup(player: Player) {
        playerPages.remove(player)
        // Caffeine handles cleanup automatically based on expiration policy
    }
}
