package com.pvpkits.cosmetics

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class CosmeticsCommand(private val plugin: PvPKitsPlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cOnly players can use cosmetics!")
            return true
        }
        
        if (args.isEmpty()) {
            openCosmeticsMenu(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "kill" -> handleKillEffect(sender, args)
            "death" -> handleDeathAnimation(sender, args)
            "trail" -> handleTrailEffect(sender, args)
            "victory" -> handleVictoryPose(sender, args)
            "clear" -> handleClear(sender, args)
            else -> openCosmeticsMenu(sender)
        }
        
        return true
    }
    
    private fun handleKillEffect(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            listKillEffects(player)
            return
        }
        
        val effectName = args[1].uppercase()
        val effect = try {
            KillEffect.valueOf(effectName)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(TextUtils.format("<red>Invalid kill effect!"))
            listKillEffects(player)
            return
        }
        
        if (!player.hasPermission(effect.permission)) {
            player.sendMessage(TextUtils.format("<red>You don't have permission for this effect!"))
            return
        }
        
        plugin.cosmeticsManager.setKillEffect(player.uniqueId, effect)
        player.sendMessage(TextUtils.format("<green>✓ Kill effect set to: <yellow>${effect.displayName}"))
    }
    
    private fun handleDeathAnimation(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            listDeathAnimations(player)
            return
        }
        
        val animName = args[1].uppercase()
        val animation = try {
            DeathAnimation.valueOf(animName)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(TextUtils.format("<red>Invalid death animation!"))
            listDeathAnimations(player)
            return
        }
        
        if (!player.hasPermission(animation.permission)) {
            player.sendMessage(TextUtils.format("<red>You don't have permission for this animation!"))
            return
        }
        
        plugin.cosmeticsManager.setDeathAnimation(player.uniqueId, animation)
        player.sendMessage(TextUtils.format("<green>✓ Death animation set to: <yellow>${animation.displayName}"))
    }
    
    private fun handleTrailEffect(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            listTrailEffects(player)
            return
        }
        
        val trailName = args[1].uppercase()
        val trail = try {
            TrailEffect.valueOf(trailName)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(TextUtils.format("<red>Invalid trail effect!"))
            listTrailEffects(player)
            return
        }
        
        if (!player.hasPermission(trail.permission)) {
            player.sendMessage(TextUtils.format("<red>You don't have permission for this trail!"))
            return
        }
        
        plugin.cosmeticsManager.setTrailEffect(player.uniqueId, trail)
        player.sendMessage(TextUtils.format("<green>✓ Trail effect set to: <yellow>${trail.displayName}"))
    }
    
    private fun handleVictoryPose(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            listVictoryPoses(player)
            return
        }
        
        val poseName = args[1].uppercase()
        val pose = try {
            VictoryPose.valueOf(poseName)
        } catch (e: IllegalArgumentException) {
            player.sendMessage(TextUtils.format("<red>Invalid victory pose!"))
            listVictoryPoses(player)
            return
        }
        
        if (!player.hasPermission(pose.permission)) {
            player.sendMessage(TextUtils.format("<red>You don't have permission for this pose!"))
            return
        }
        
        plugin.cosmeticsManager.setVictoryPose(player.uniqueId, pose)
        player.sendMessage(TextUtils.format("<green>✓ Victory pose set to: <yellow>${pose.displayName}"))
    }
    
    private fun handleClear(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(TextUtils.format("<red>Usage: /cosmetics clear <kill|death|trail|victory|all>"))
            return
        }
        
        when (args[1].lowercase()) {
            "kill" -> {
                plugin.cosmeticsManager.setKillEffect(player.uniqueId, null)
                player.sendMessage(TextUtils.format("<yellow>Cleared kill effect"))
            }
            "death" -> {
                plugin.cosmeticsManager.setDeathAnimation(player.uniqueId, null)
                player.sendMessage(TextUtils.format("<yellow>Cleared death animation"))
            }
            "trail" -> {
                plugin.cosmeticsManager.setTrailEffect(player.uniqueId, null)
                player.sendMessage(TextUtils.format("<yellow>Cleared trail effect"))
            }
            "victory" -> {
                plugin.cosmeticsManager.setVictoryPose(player.uniqueId, null)
                player.sendMessage(TextUtils.format("<yellow>Cleared victory pose"))
            }
            "all" -> {
                plugin.cosmeticsManager.setKillEffect(player.uniqueId, null)
                plugin.cosmeticsManager.setDeathAnimation(player.uniqueId, null)
                plugin.cosmeticsManager.setTrailEffect(player.uniqueId, null)
                plugin.cosmeticsManager.setVictoryPose(player.uniqueId, null)
                player.sendMessage(TextUtils.format("<yellow>Cleared all cosmetics"))
            }
        }
    }
    
    private fun openCosmeticsMenu(player: Player) {
        val cosmetics = plugin.cosmeticsManager.getCosmetics(player.uniqueId)
        
        player.sendMessage("")
        player.sendMessage(TextUtils.format("<gradient:#ff00ff:#ff6bff><bold>═══ COSMETICS ═══</bold></gradient>"))
        player.sendMessage(TextUtils.format("<yellow>/cosmetics kill</yellow> <gray>- Kill effects"))
        player.sendMessage(TextUtils.format("<yellow>/cosmetics death</yellow> <gray>- Death animations"))
        player.sendMessage(TextUtils.format("<yellow>/cosmetics trail</yellow> <gray>- Trail effects"))
        player.sendMessage(TextUtils.format("<yellow>/cosmetics victory</yellow> <gray>- Victory poses"))
        player.sendMessage("")
        player.sendMessage(TextUtils.format("<gray>Current:"))
        player.sendMessage(TextUtils.format("  <gray>Kill: <yellow>${cosmetics.killEffect?.displayName ?: "None"}"))
        player.sendMessage(TextUtils.format("  <gray>Death: <yellow>${cosmetics.deathAnimation?.displayName ?: "None"}"))
        player.sendMessage(TextUtils.format("  <gray>Trail: <yellow>${cosmetics.trailEffect?.displayName ?: "None"}"))
        player.sendMessage(TextUtils.format("  <gray>Victory: <yellow>${cosmetics.victoryPose?.displayName ?: "None"}"))
        player.sendMessage(TextUtils.format("<gradient:#ff00ff:#ff6bff><bold>═══════════════════</bold></gradient>"))
        player.sendMessage("")
    }
    
    private fun listKillEffects(player: Player) {
        player.sendMessage(TextUtils.format("<gradient:#ff0000:#ff6b6b><bold>═══ KILL EFFECTS ═══</bold></gradient>"))
        KillEffect.values().forEach { effect ->
            val status = if (player.hasPermission(effect.permission)) "§a✓" else "§c✗"
            player.sendMessage(TextUtils.format("  $status <yellow>${effect.name.lowercase()}</yellow> <gray>- ${effect.displayName}"))
        }
    }
    
    private fun listDeathAnimations(player: Player) {
        player.sendMessage(TextUtils.format("<gradient:#ff0000:#ff6b6b><bold>═══ DEATH ANIMATIONS ═══</bold></gradient>"))
        DeathAnimation.values().forEach { anim ->
            val status = if (player.hasPermission(anim.permission)) "§a✓" else "§c✗"
            player.sendMessage(TextUtils.format("  $status <yellow>${anim.name.lowercase()}</yellow> <gray>- ${anim.displayName}"))
        }
    }
    
    private fun listTrailEffects(player: Player) {
        player.sendMessage(TextUtils.format("<gradient:#00ffff:#00aaff><bold>═══ TRAIL EFFECTS ═══</bold></gradient>"))
        TrailEffect.values().forEach { trail ->
            val status = if (player.hasPermission(trail.permission)) "§a✓" else "§c✗"
            player.sendMessage(TextUtils.format("  $status <yellow>${trail.name.lowercase()}</yellow> <gray>- ${trail.displayName}"))
        }
    }
    
    private fun listVictoryPoses(player: Player) {
        player.sendMessage(TextUtils.format("<gradient:#ffd700:#ffaa00><bold>═══ VICTORY POSES ═══</bold></gradient>"))
        VictoryPose.values().forEach { pose ->
            val status = if (player.hasPermission(pose.permission)) "§a✓" else "§c✗"
            player.sendMessage(TextUtils.format("  $status <yellow>${pose.name.lowercase()}</yellow> <gray>- ${pose.displayName}"))
        }
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        return when (args.size) {
            1 -> listOf("kill", "death", "trail", "victory", "clear")
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "kill" -> KillEffect.values().map { it.name.lowercase() }
                "death" -> DeathAnimation.values().map { it.name.lowercase() }
                "trail" -> TrailEffect.values().map { it.name.lowercase() }
                "victory" -> VictoryPose.values().map { it.name.lowercase() }
                "clear" -> listOf("kill", "death", "trail", "victory", "all")
                else -> emptyList()
            }.filter { it.startsWith(args[1], ignoreCase = true) }
            else -> emptyList()
        }
    }
}
