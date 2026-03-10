package com.pvpkits.combat

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Combat mechanics manager for duel PvP.
 */
class CombatMechanicsManager(private val plugin: PvPKitsPlugin) : Listener {

    private val combos = ConcurrentHashMap<UUID, ComboData>()
    private val sprintData = ConcurrentHashMap<UUID, SprintData>()

    data class ComboData(
        var hits: Int = 0,
        var lastHitTime: Long = 0,
        var lastVictim: UUID? = null,
        var maxCombo: Int = 0
    )

    data class SprintData(
        var sprintToggles: MutableList<Long> = mutableListOf(),
        var lastToggleTime: Long = 0
    )

    companion object {
        private const val COMBO_TIMEOUT = 3000L
        private const val WTAP_WINDOW = 500L
    }

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.server.scheduler.runTaskTimer(plugin, Runnable { cleanupOldData() }, 600L, 600L)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return

        if (!plugin.duelManager.isInMatch(attacker.uniqueId)) return
        if (!plugin.duelManager.isInMatch(victim.uniqueId)) return

        val attackerUUID = attacker.uniqueId
        val victimUUID = victim.uniqueId

        updateCombo(attackerUUID, victimUUID, attacker)

        if (isCriticalHit(attacker)) {
            handleCriticalHit(attacker, victim, event)
        }

        if (isWTap(attackerUUID)) {
            handleWTap(attacker, victim)
        }

        if (isSweepAttack(attacker)) {
            handleSweepAttack(attacker, victim)
        }
    }

    @EventHandler
    fun onSprintToggle(event: PlayerToggleSprintEvent) {
        val uuid = event.player.uniqueId
        val now = System.currentTimeMillis()

        val data = sprintData.getOrPut(uuid) { SprintData() }
        data.sprintToggles.add(now)
        data.lastToggleTime = now

        if (data.sprintToggles.size > 10) {
            data.sprintToggles.removeAt(0)
        }
    }

    private fun updateCombo(attackerUUID: UUID, victimUUID: UUID, attacker: Player) {
        val now = System.currentTimeMillis()
        val data = combos.getOrPut(attackerUUID) { ComboData() }

        if (now - data.lastHitTime > COMBO_TIMEOUT || data.lastVictim != victimUUID) {
            if (data.hits > 0) {
                sendComboEndMessage(attacker, data.hits)
            }
            data.hits = 0
        }

        data.hits++
        data.lastHitTime = now
        data.lastVictim = victimUUID

        if (data.hits > data.maxCombo) {
            data.maxCombo = data.hits
        }

        if (data.hits >= 3) {
            showCombo(attacker, data.hits)
        }
    }

    private fun isCriticalHit(player: Player): Boolean {
        return player.fallDistance > 0.0 &&
            !player.isOnGround &&
            !player.isInWater &&
            !player.isClimbing &&
            player.velocity.y < 0
    }

    private fun handleCriticalHit(attacker: Player, victim: Player, event: EntityDamageByEntityEvent) {
        event.damage *= 1.5

        attacker.sendMessage(TextUtils.parseAuto("<red><bold>Critical hit!"))
        victim.world.spawnParticle(
            Particle.CRIT,
            victim.location.add(0.0, 1.0, 0.0),
            20,
            0.3,
            0.5,
            0.3,
            0.1
        )

        attacker.playSound(attacker.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f)
    }

    private fun isWTap(uuid: UUID): Boolean {
        val data = sprintData[uuid] ?: return false
        val now = System.currentTimeMillis()
        return data.sprintToggles.count { now - it < WTAP_WINDOW } >= 2
    }

    private fun handleWTap(attacker: Player, victim: Player) {
        val direction = victim.location.subtract(attacker.location).toVector().normalize()
        victim.velocity = direction.multiply(0.5).setY(0.2)
        attacker.sendMessage(TextUtils.parseAuto("<yellow>W-Tap!"))
    }

    private fun isSweepAttack(player: Player): Boolean {
        val item = player.inventory.itemInMainHand
        return item.type.name.contains("SWORD") && player.attackCooldown >= 0.9f
    }

    private fun handleSweepAttack(attacker: Player, victim: Player) {
        attacker.sendMessage(TextUtils.parseAuto("<aqua>Sweep Attack!"))
        victim.world.spawnParticle(Particle.SWEEP_ATTACK, victim.location.add(0.0, 1.0, 0.0), 3)
    }

    private fun showCombo(player: Player, hits: Int) {
        val color = when {
            hits >= 10 -> "&c&l"
            hits >= 7 -> "&6&l"
            hits >= 5 -> "&e&l"
            else -> "&a"
        }

        player.sendActionBar(TextUtils.parseLegacy("${color}COMBO: $hits"))

        if (hits % 5 == 0) {
            player.playSound(player.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f)
        }
    }

    private fun sendComboEndMessage(player: Player, hits: Int) {
        if (hits < 3) return

        val message = when {
            hits >= 10 -> "<red><bold>Insane combo: $hits!"
            hits >= 7 -> "<gold><bold>Great combo: $hits!"
            hits >= 5 -> "<yellow><bold>Nice combo: $hits!"
            else -> "<green>Combo: $hits"
        }

        player.sendMessage(TextUtils.parseAuto(message))
    }

    fun getCurrentCombo(uuid: UUID): Int = combos[uuid]?.hits ?: 0

    fun getMaxCombo(uuid: UUID): Int = combos[uuid]?.maxCombo ?: 0

    fun resetCombo(uuid: UUID) {
        combos.remove(uuid)
    }

    private fun cleanupOldData() {
        val now = System.currentTimeMillis()

        combos.entries.removeIf { (_, data) ->
            now - data.lastHitTime > COMBO_TIMEOUT * 2
        }

        sprintData.entries.removeIf { (_, data) ->
            now - data.lastToggleTime > 60000
        }
    }

    fun cleanupPlayer(uuid: UUID) {
        combos.remove(uuid)
        sprintData.remove(uuid)
    }

    fun getStats(): Map<String, Any> {
        return mapOf(
            "active_combos" to combos.size,
            "tracked_players" to sprintData.size
        )
    }
}
