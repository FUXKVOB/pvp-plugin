package com.pvpkits.utils

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.pvpkits.PvPKitsPlugin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask

/**
 * Scheduler utilities - simplified version without Folia support for now
 * Uses standard Bukkit scheduler with MCCoroutine integration
 */
object SchedulerUtils {
    
    /**
     * Run task on main thread
     */
    fun runTask(plugin: PvPKitsPlugin, runnable: Runnable): BukkitTask {
        return Bukkit.getScheduler().runTask(plugin, runnable)
    }
    
    /**
     * Run task later
     */
    fun runTaskLater(plugin: PvPKitsPlugin, delay: Long, runnable: Runnable): BukkitTask {
        return Bukkit.getScheduler().runTaskLater(plugin, runnable, delay)
    }
    
    /**
     * Run task timer
     */
    fun runTaskTimer(plugin: PvPKitsPlugin, delay: Long, period: Long, runnable: Runnable): BukkitTask {
        return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period)
    }
    
    /**
     * Run async task
     */
    fun runAsync(plugin: PvPKitsPlugin, runnable: Runnable): BukkitTask {
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable)
    }
    
    /**
     * Cancel all tasks for plugin
     */
    fun cancelAllTasks(plugin: PvPKitsPlugin) {
        Bukkit.getScheduler().cancelTasks(plugin)
    }
    
    /**
     * Execute on main thread (for coroutines)
     */
    suspend fun <T> withMainThread(plugin: PvPKitsPlugin, block: suspend () -> T): T {
        return withContext(plugin.minecraftDispatcher) {
            block()
        }
    }
}
