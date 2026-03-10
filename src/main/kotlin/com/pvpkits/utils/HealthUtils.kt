package com.pvpkits.utils

import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player

object HealthUtils {

    fun maxHealth(player: Player): Double {
        return player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
    }

    fun reset(player: Player) {
        player.health = maxHealth(player).coerceAtLeast(1.0)
    }
}
