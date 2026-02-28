package com.pvpkits.spectator

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import java.util.*

data class SpectatorSession(
    val originalLocation: Location,
    val originalGameMode: GameMode,
    val originalInventory: Array<ItemStack?>,
    val originalArmor: Array<ItemStack?>,
    val originalHealth: Double,
    val originalFoodLevel: Int,
    val targetUUID: UUID,
    val startTime: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpectatorSession

        if (targetUUID != other.targetUUID) return false
        if (startTime != other.startTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = targetUUID.hashCode()
        result = 31 * result + startTime.hashCode()
        return result
    }
}
