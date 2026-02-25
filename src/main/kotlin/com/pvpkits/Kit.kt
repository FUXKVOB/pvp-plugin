package com.pvpkits

import org.bukkit.inventory.ItemStack

data class Kit(
    val name: String,
    val displayName: String,
    val icon: String?,
    val permission: String?,
    val cooldown: Int,
    val description: List<String>,
    val items: List<ItemStack>
)
