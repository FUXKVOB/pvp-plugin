package com.pvpkits.combat

import com.pvpkits.PvPKitsPlugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Combat Mechanics Manager для 1.21+
 * 
 * Отслеживает:
 * - W-tapping (новая механика спринта 1.21)
 * - Combo counter
 * - Critical hits
 * - Sweep attacks
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
        private const val COMBO_TIMEOUT = 3000L // 3 секунды
        private const val WTAP_WINDOW = 500L // 500ms для W-tap
    }
    
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        
        // Очистка старых данных каждые 30 секунд
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            cleanupOldData()
        }, 600L, 600L)
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return
        
        // Проверяем что это PvP в дуэли
        if (!plugin.duelManager.isInMatch(attacker.uniqueId)) return
        if (!plugin.duelManager.isInMatch(victim.uniqueId)) return
        
        val attackerUUID = attacker.uniqueId
        val victimUUID = victim.uniqueId
        
        // Обновляем комбо
        updateCombo(attackerUUID, victimUUID, attacker)
        
        // Проверяем критический удар
        if (isCriticalHit(attacker)) {
            handleCriticalHit(attacker, victim, event)
        }
        
        // Проверяем W-tap
        if (isWTap(attackerUUID)) {
            handleWTap(attacker, victim)
        }
        
        // Проверяем sweep attack
        if (isSweepAttack(attacker)) {
            handleSweepAttack(attacker, victim)
        }
    }
    
    @EventHandler
    fun onSprintToggle(event: PlayerToggleSprintEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()
        
        val data = sprintData.getOrPut(uuid) { SprintData() }
        data.sprintToggles.add(now)
        data.lastToggleTime = now
        
        // Храним только последние 10 переключений
        if (data.sprintToggles.size > 10) {
            data.sprintToggles.removeAt(0)
        }
    }
    
    /**
     * Обновить комбо счетчик
     */
    private fun updateCombo(attackerUUID: UUID, victimUUID: UUID, attacker: Player) {
        val now = System.currentTimeMillis()
        val data = combos.getOrPut(attackerUUID) { ComboData() }
        
        // Проверяем таймаут комбо
        if (now - data.lastHitTime > COMBO_TIMEOUT || data.lastVictim != victimUUID) {
            // Комбо прервано
            if (data.hits > 0) {
                sendComboEndMessage(attacker, data.hits)
            }
            data.hits = 0
        }
        
        // Увеличиваем комбо
        data.hits++
        data.lastHitTime = now
        data.lastVictim = victimUUID
        
        if (data.hits > data.maxCombo) {
            data.maxCombo = data.hits
        }
        
        // Показываем комбо
        if (data.hits >= 3) {
            showCombo(attacker, data.hits)
        }
    }
    
    /**
     * Проверить критический удар
     */
    private fun isCriticalHit(player: Player): Boolean {
        return player.fallDistance > 0.0 && 
               !player.isOnGround && 
               !player.isInWater &&
               !player.isClimbing &&
               player.velocity.y < 0
    }
    
    /**
     * Обработать критический удар
     */
    private fun handleCriticalHit(attacker: Player, victim: Player, event: EntityDamageByEntityEvent) {
        // Увеличиваем урон на 50%
        event.damage *= 1.5
        
        // Эффекты
        attacker.sendMessage("§c§l⚡ КРИТИЧЕСКИЙ УДАР!")
        victim.world.spawnParticle(
            org.bukkit.Particle.CRIT,
            victim.location.add(0.0, 1.0, 0.0),
            20,
            0.3, 0.5, 0.3,
            0.1
        )
        
        attacker.playSound(attacker.location, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f)
    }
    
    /**
     * Проверить W-tap (1.21 механика)
     */
    private fun isWTap(uuid: UUID): Boolean {
        val data = sprintData[uuid] ?: return false
        val now = System.currentTimeMillis()
        
        // W-tap = быстрое переключение спринта
        val recentToggles = data.sprintToggles.count { now - it < WTAP_WINDOW }
        return recentToggles >= 2
    }
    
    /**
     * Обработать W-tap
     */
    private fun handleWTap(attacker: Player, victim: Player) {
        // W-tap дает небольшой бонус к отбрасыванию
        val direction = victim.location.subtract(attacker.location).toVector().normalize()
        victim.velocity = direction.multiply(0.5).setY(0.2)
        
        attacker.sendMessage("§e⚡ W-Tap!")
    }
    
    /**
     * Проверить sweep attack
     */
    private fun isSweepAttack(player: Player): Boolean {
        val item = player.inventory.itemInMainHand
        return item.type.name.contains("SWORD") && 
               player.attackCooldown >= 0.9f
    }
    
    /**
     * Обработать sweep attack
     */
    private fun handleSweepAttack(attacker: Player, victim: Player) {
        attacker.sendMessage("§b⚔ Sweep Attack!")
        
        // Частицы sweep
        victim.world.spawnParticle(
            org.bukkit.Particle.SWEEP_ATTACK,
            victim.location.add(0.0, 1.0, 0.0),
            3
        )
    }
    
    /**
     * Показать комбо
     */
    private fun showCombo(player: Player, hits: Int) {
        val color = when {
            hits >= 10 -> "§c§l"
            hits >= 7 -> "§6§l"
            hits >= 5 -> "§e§l"
            else -> "§a"
        }
        
        player.sendActionBar("${color}COMBO: $hits")
        
        // Звук при каждом 5-м ударе
        if (hits % 5 == 0) {
            player.playSound(player.location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f)
        }
    }
    
    /**
     * Сообщение об окончании комбо
     */
    private fun sendComboEndMessage(player: Player, hits: Int) {
        if (hits < 3) return
        
        val message = when {
            hits >= 10 -> "§c§l⚔ НЕВЕРОЯТНОЕ КОМБО: $hits!"
            hits >= 7 -> "§6§l⚔ ОТЛИЧНОЕ КОМБО: $hits!"
            hits >= 5 -> "§e§l⚔ ХОРОШЕЕ КОМБО: $hits!"
            else -> "§a⚔ Комбо: $hits"
        }
        
        player.sendMessage(message)
    }
    
    /**
     * Получить текущее комбо
     */
    fun getCurrentCombo(uuid: UUID): Int {
        return combos[uuid]?.hits ?: 0
    }
    
    /**
     * Получить максимальное комбо
     */
    fun getMaxCombo(uuid: UUID): Int {
        return combos[uuid]?.maxCombo ?: 0
    }
    
    /**
     * Сбросить комбо
     */
    fun resetCombo(uuid: UUID) {
        combos.remove(uuid)
    }
    
    /**
     * Очистка старых данных
     */
    private fun cleanupOldData() {
        val now = System.currentTimeMillis()
        
        // Очистка комбо
        combos.entries.removeIf { (_, data) ->
            now - data.lastHitTime > COMBO_TIMEOUT * 2
        }
        
        // Очистка sprint data
        sprintData.entries.removeIf { (_, data) ->
            now - data.lastToggleTime > 60000 // 1 минута
        }
    }
    
    /**
     * Очистка игрока
     */
    fun cleanupPlayer(uuid: UUID) {
        combos.remove(uuid)
        sprintData.remove(uuid)
    }
    
    /**
     * Получить статистику
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "active_combos" to combos.size,
            "tracked_players" to sprintData.size
        )
    }
}
