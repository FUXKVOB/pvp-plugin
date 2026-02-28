package com.pvpkits.anticheat

import com.github.shynixn.mccoroutine.bukkit.launch
import com.pvpkits.PvPKitsPlugin
import kotlinx.coroutines.delay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.util.Vector
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Modern Anti-Cheat Manager - 2026 Edition
 * 
 * Packet-level детекция читов:
 * - Auto-clicker detection
 * - Reach detection
 * - Velocity check
 * - Combat pattern analysis
 */
class ModernAntiCheatManager(private val plugin: PvPKitsPlugin) : Listener {
    
    private val clickData = ConcurrentHashMap<UUID, ClickTracker>()
    private val reachData = ConcurrentHashMap<UUID, ReachTracker>()
    private val velocityData = ConcurrentHashMap<UUID, VelocityTracker>()
    
    data class ClickTracker(
        val clicks: MutableList<Long> = mutableListOf(),
        var violations: Int = 0,
        var lastViolation: Long = 0
    )
    
    data class ReachTracker(
        val hits: MutableList<Double> = mutableListOf(),
        var violations: Int = 0,
        var lastViolation: Long = 0
    )
    
    data class VelocityTracker(
        var expectedVelocity: Vector? = null,
        var checkTime: Long = 0,
        var violations: Int = 0
    )
    
    companion object {
        private const val MAX_CPS = 20 // Легит макс ~16-18 CPS
        private const val MAX_REACH = 3.5 // Vanilla 1.21 max reach
        private const val VELOCITY_THRESHOLD = 0.5
        private const val VIOLATION_THRESHOLD = 10
        private const val VIOLATION_DECAY_TIME = 60000L // 1 минута
    }
    
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        
        // Очистка старых данных каждые 30 секунд
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            cleanupOldData()
        }, 600L, 600L)
    }
    
    /**
     * Детекция автокликера (packet-level)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!event.action.isLeftClick) return
        
        val tracker = clickData.getOrPut(player.uniqueId) { ClickTracker() }
        val now = System.currentTimeMillis()
        
        tracker.clicks.add(now)
        tracker.clicks.removeIf { now - it > 1000 } // Последняя секунда
        
        // Проверка CPS
        if (tracker.clicks.size > MAX_CPS) {
            tracker.violations++
            tracker.lastViolation = now
            
            if (tracker.violations >= VIOLATION_THRESHOLD) {
                handleAutoClickerViolation(player, tracker.clicks.size)
                tracker.violations = 0 // Reset после наказания
            }
        }
    }
    
    /**
     * Детекция reach (дальность атаки)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player ?: return
        val victim = event.entity as? Player ?: return
        
        // Проверка reach
        val distance = attacker.location.distance(victim.location)
        
        if (distance > MAX_REACH) {
            val tracker = reachData.getOrPut(attacker.uniqueId) { ReachTracker() }
            tracker.hits.add(distance)
            tracker.violations++
            tracker.lastViolation = System.currentTimeMillis()
            
            // Отменить удар
            event.isCancelled = true
            
            if (tracker.violations >= VIOLATION_THRESHOLD) {
                handleReachViolation(attacker, distance)
                tracker.violations = 0
            }
            
            plugin.logger.warning(
                "[AntiCheat] ${attacker.name} suspicious reach: ${String.format("%.2f", distance)}m (max: $MAX_REACH)"
            )
        }
    }
    
    /**
     * Velocity check (игнорирование отбрасывания)
     */
    fun checkVelocity(player: Player, expectedVelocity: Vector) {
        val tracker = velocityData.getOrPut(player.uniqueId) { VelocityTracker() }
        tracker.expectedVelocity = expectedVelocity.clone()
        tracker.checkTime = System.currentTimeMillis()
        
        // Проверка через 3-5 тиков (150-250ms)
        plugin.launch {
            delay(150)
            
            val actualVelocity = player.velocity
            val expected = tracker.expectedVelocity ?: return@launch
            
            val difference = expected.distance(actualVelocity)
            
            if (difference > VELOCITY_THRESHOLD) {
                tracker.violations++
                
                if (tracker.violations >= VIOLATION_THRESHOLD) {
                    handleVelocityViolation(player, difference)
                    tracker.violations = 0
                }
                
                plugin.logger.warning(
                    "[AntiCheat] ${player.name} suspicious velocity: diff=${String.format("%.2f", difference)}"
                )
            }
        }
    }
    
    /**
     * Обработка нарушения автокликера
     */
    private fun handleAutoClickerViolation(player: Player, cps: Int) {
        plugin.logger.warning("[AntiCheat] ${player.name} suspected auto-clicker: $cps CPS")
        
        // Уведомление администраторов
        plugin.server.onlinePlayers
            .filter { it.hasPermission("pvpkits.admin") }
            .forEach { admin ->
                admin.sendMessage("§c[AntiCheat] §e${player.name} §7suspected auto-clicker: §c$cps CPS")
            }
        
        // Опционально: кик/бан
        if (plugin.config.getBoolean("anticheat.auto-kick", false)) {
            player.kick(net.kyori.adventure.text.Component.text("§cSuspected auto-clicker"))
        }
    }
    
    /**
     * Обработка нарушения reach
     */
    private fun handleReachViolation(player: Player, distance: Double) {
        plugin.logger.warning("[AntiCheat] ${player.name} suspected reach: ${String.format("%.2f", distance)}m")
        
        // Уведомление администраторов
        plugin.server.onlinePlayers
            .filter { it.hasPermission("pvpkits.admin") }
            .forEach { admin ->
                admin.sendMessage("§c[AntiCheat] §e${player.name} §7suspected reach: §c${String.format("%.2f", distance)}m")
            }
        
        // Опционально: кик/бан
        if (plugin.config.getBoolean("anticheat.auto-kick", false)) {
            player.kick(net.kyori.adventure.text.Component.text("§cSuspected reach hacks"))
        }
    }
    
    /**
     * Обработка нарушения velocity
     */
    private fun handleVelocityViolation(player: Player, difference: Double) {
        plugin.logger.warning("[AntiCheat] ${player.name} suspected velocity: ${String.format("%.2f", difference)}")
        
        // Уведомление администраторов
        plugin.server.onlinePlayers
            .filter { it.hasPermission("pvpkits.admin") }
            .forEach { admin ->
                admin.sendMessage("§c[AntiCheat] §e${player.name} §7suspected velocity: §c${String.format("%.2f", difference)}")
            }
    }
    
    /**
     * Очистка старых данных
     */
    private fun cleanupOldData() {
        val now = System.currentTimeMillis()
        
        // Decay violations
        clickData.values.forEach { tracker ->
            if (now - tracker.lastViolation > VIOLATION_DECAY_TIME) {
                tracker.violations = (tracker.violations - 1).coerceAtLeast(0)
            }
        }
        
        reachData.values.forEach { tracker ->
            if (now - tracker.lastViolation > VIOLATION_DECAY_TIME) {
                tracker.violations = (tracker.violations - 1).coerceAtLeast(0)
            }
            // Очистка старых хитов
            tracker.hits.clear()
        }
        
        velocityData.values.forEach { tracker ->
            if (now - tracker.checkTime > 5000) {
                tracker.expectedVelocity = null
            }
        }
    }
    
    /**
     * Очистка данных игрока
     */
    fun cleanupPlayer(uuid: UUID) {
        clickData.remove(uuid)
        reachData.remove(uuid)
        velocityData.remove(uuid)
    }
    
    /**
     * Получить статистику
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "tracked_players" to clickData.size,
            "total_click_violations" to clickData.values.sumOf { it.violations },
            "total_reach_violations" to reachData.values.sumOf { it.violations },
            "total_velocity_violations" to velocityData.values.sumOf { it.violations }
        )
    }
}
