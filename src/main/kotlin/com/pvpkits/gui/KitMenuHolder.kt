package com.pvpkits.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class KitMenuHolder : InventoryHolder {
    override fun getInventory(): Inventory {
        throw UnsupportedOperationException("KitMenuHolder does not keep a direct inventory reference")
    }
}
