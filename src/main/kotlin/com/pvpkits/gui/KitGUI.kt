package com.pvpkits.gui

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.pvpkits.Kit
import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.ComponentCache
import com.pvpkits.utils.TextUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

class KitGUI(private val plugin: PvPKitsPlugin) {

    private val playerPages = mutableMapOf<Player, Int>()

    private val kitIconCache: Cache<String, ItemStack> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .recordStats()
        .build()

    fun openKitMenu(player: Player, page: Int = 1) {
        val kits = plugin.kitManager.getAllKits().toList()
        val itemsPerPage = plugin.config.getInt("gui.items-per-page", 28)
        val totalPages = ceil(kits.size.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
        val currentPage = page.coerceIn(1, totalPages)

        playerPages[player] = currentPage

        val rows = plugin.config.getInt("gui.rows", 6)
        val title = TextUtils.parseAuto(plugin.config.getString("gui.title") ?: "<gold><bold>PvP Kits")
        val inventory = Bukkit.createInventory(KitMenuHolder(), rows * 9, title)

        fillBorders(inventory, rows)

        val startIndex = (currentPage - 1) * itemsPerPage
        val endIndex = (startIndex + itemsPerPage).coerceAtMost(kits.size)
        val pageKits = kits.subList(startIndex, endIndex)

        val kitSlots = getKitSlots(rows)
        pageKits.forEachIndexed { index, kit ->
            if (index < kitSlots.size) {
                inventory.setItem(kitSlots[index], createKitIcon(kit, player))
            }
        }

        if (currentPage > 1) {
            inventory.setItem(rows * 9 - 9, createPreviousPageButton())
        }

        if (currentPage < totalPages) {
            inventory.setItem(rows * 9 - 1, createNextPageButton())
        }

        inventory.setItem(rows * 9 - 5, createInfoButton(currentPage, totalPages, kits.size))
        player.openInventory(inventory)
        playSound(player, "gui-click")
    }

    private fun fillBorders(inventory: Inventory, rows: Int) {
        val borderPane = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta!!.apply { displayName(TextUtils.parseAuto("<gray> ")) }
        }

        val accentPane = ItemStack(Material.RED_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta!!.apply { displayName(TextUtils.parseAuto("<red> ")) }
        }

        for (i in 0 until 9) {
            inventory.setItem(i, if (i % 2 == 0) accentPane else borderPane)
            inventory.setItem((rows - 1) * 9 + i, if (i % 2 == 0) accentPane else borderPane)
        }

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
        val cacheKey = buildCacheKey(
            kit.name,
            player.uniqueId,
            kit.permission,
            plugin.kitManager.hasCooldown(player.uniqueId, kit.name),
            plugin.kitManager.getCooldownRemaining(player.uniqueId, kit.name) / 10
        )

        kitIconCache.getIfPresent(cacheKey)?.let { return it.clone() }

        val iconMaterial = kit.icon?.let { Material.getMaterial(it) }
            ?: kit.items.firstOrNull()?.type
            ?: Material.CHEST

        val item = ItemStack(iconMaterial)
        val meta = item.itemMeta!!
        meta.displayName(TextUtils.parseAuto(kit.displayName))
        meta.persistentDataContainer.set(plugin.itemKeys.guiAction, PersistentDataType.STRING, "kit")
        meta.persistentDataContainer.set(plugin.itemKeys.guiKitName, PersistentDataType.STRING, kit.name)

        val lore = mutableListOf<String>()
        kit.description.forEach { lore.add(it) }
        lore.add("")
        lore.add("<gray>Items: <white>${kit.items.size}")
        lore.add("<gray>Cooldown: <white>${formatTime(kit.cooldown.toLong())}")

        if (kit.permission != null && !player.hasPermission(kit.permission)) {
            lore.add("")
            lore.add("<red>No permission")
            lore.add("<gray>Required: <white>${kit.permission}")
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true)
        } else if (plugin.kitManager.hasCooldown(player.uniqueId, kit.name)) {
            val remaining = plugin.kitManager.getCooldownRemaining(player.uniqueId, kit.name)
            lore.add("")
            lore.add("<red>Cooldown: <white>${formatTime(remaining)}")
            meta.addEnchant(Enchantment.BINDING_CURSE, 1, true)
        } else {
            lore.add("")
            lore.add("<green>Click to claim!")
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true)
        }

        meta.lore(lore.map(TextUtils::parseAuto))
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES)
        item.itemMeta = meta

        kitIconCache.put(cacheKey, item.clone())
        return item
    }

    private fun buildCacheKey(
        kitName: String,
        uuid: java.util.UUID,
        permission: String?,
        hasCooldown: Boolean,
        cooldownBucket: Long
    ): String {
        val permKey = if (permission != null) "perm_$permission" else "no_perm"
        val cooldownKey = if (hasCooldown) "cd_$cooldownBucket" else "no_cd"
        return "${kitName}_${uuid}_${permKey}_${cooldownKey}"
    }

    fun invalidateKitCache(kitName: String) {
        kitIconCache.invalidateAll()
    }

    fun clearAllCache() {
        kitIconCache.invalidateAll()
    }

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

    fun getCacheStatsFormatted(): String {
        val stats = kitIconCache.stats()
        return "Item Cache - Hits: ${stats.hitCount()}, Misses: ${stats.missCount()}, Hit Rate: ${String.format("%.2f", stats.hitRate() * 100)}%"
    }

    private fun createPreviousPageButton(): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta!!
        meta.displayName(TextUtils.parseAuto("<yellow>Previous Page"))
        meta.persistentDataContainer.set(plugin.itemKeys.guiAction, PersistentDataType.STRING, "previous")
        meta.lore(TextUtils.lines("<gray>Click to go back"))
        item.itemMeta = meta
        return item
    }

    private fun createNextPageButton(): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta!!
        meta.displayName(TextUtils.parseAuto("<yellow>Next Page"))
        meta.persistentDataContainer.set(plugin.itemKeys.guiAction, PersistentDataType.STRING, "next")
        meta.lore(TextUtils.lines("<gray>Click to continue"))
        item.itemMeta = meta
        return item
    }

    private fun createInfoButton(currentPage: Int, totalPages: Int, totalKits: Int): ItemStack {
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta!!
        meta.displayName(TextUtils.parseAuto("<aqua><bold>Information"))
        meta.persistentDataContainer.set(plugin.itemKeys.guiAction, PersistentDataType.STRING, "info")
        meta.lore(
            TextUtils.lines(
                "<gray>Page: <white>$currentPage/$totalPages",
                "<gray>Total Kits: <white>$totalKits",
                "<yellow>Choose your kit wisely!"
            )
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
                val currentPage = playerPages[player] ?: 1
                when (clickedItem.itemMeta?.persistentDataContainer?.get(plugin.itemKeys.guiAction, PersistentDataType.STRING)) {
                    "previous" -> {
                        openKitMenu(player, currentPage - 1)
                        playSound(player, "page-turn")
                    }
                    "next" -> {
                        openKitMenu(player, currentPage + 1)
                        playSound(player, "page-turn")
                    }
                }
            }
            Material.GRAY_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE, Material.BOOK -> Unit
            else -> {
                val action = clickedItem.itemMeta?.persistentDataContainer
                    ?.get(plugin.itemKeys.guiAction, PersistentDataType.STRING)
                if (action != "kit") return

                val kitName = clickedItem.itemMeta?.persistentDataContainer
                    ?.get(plugin.itemKeys.guiKitName, PersistentDataType.STRING)
                    ?: return
                val kit = plugin.kitManager.getKit(kitName) ?: return

                player.closeInventory()

                if (kit.permission != null && !player.hasPermission(kit.permission)) {
                    player.sendMessage(TextUtils.parseAuto(getMessage("no-permission")))
                    playSound(player, "kit-cooldown")
                    return
                }

                if (plugin.kitManager.hasCooldown(player.uniqueId, kit.name)) {
                    val remaining = plugin.kitManager.getCooldownRemaining(player.uniqueId, kit.name)
                    player.sendMessage(TextUtils.parseAuto(getMessage("kit-cooldown").replace("{time}", formatTime(remaining))))
                    playSound(player, "kit-cooldown")
                    return
                }

                if (plugin.kitManager.giveKit(player, kit.name)) {
                    player.sendMessage(TextUtils.parseAuto(getMessage("kit-received").replace("{kit}", kit.displayName)))
                    playSound(player, "kit-select")
                }
            }
        }
    }

    private fun getMessage(key: String): String {
        val prefix = plugin.config.getString("messages.prefix") ?: ""
        val message = plugin.config.getString("messages.$key") ?: key
        return prefix + message
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
            player.playSound(player.location, Sound.valueOf(soundName), 1.0f, 1.0f)
        } catch (_: Exception) {
        }
    }

    fun cleanup(player: Player) {
        playerPages.remove(player)
    }
}
