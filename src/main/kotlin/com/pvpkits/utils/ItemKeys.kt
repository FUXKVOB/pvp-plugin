package com.pvpkits.utils

import com.pvpkits.PvPKitsPlugin
import org.bukkit.NamespacedKey

class ItemKeys(plugin: PvPKitsPlugin) {
    val kitCompass = NamespacedKey(plugin, "kit_compass")
    val guiAction = NamespacedKey(plugin, "gui_action")
    val guiKitName = NamespacedKey(plugin, "gui_kit_name")
}
