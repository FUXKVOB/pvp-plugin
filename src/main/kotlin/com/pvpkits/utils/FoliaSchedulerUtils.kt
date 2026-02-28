package com.pvpkits.utils

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.function.Consumer

/**
 * Folia-совместимые scheduler утилиты
 * 
 * Автоматически определяет Paper или Folia и использует правильный API
 */
object FoliaSchedulerUtils {
    
    private val isFolia: Boolean by lazy {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
    
    /**
     * Запустить задачу в регионе локации (Folia-safe)
     */
    fun runAtLocation(
        plugin: Plugin,
        location: Location,
        task: Runnable
    ) {
        if (isFolia) {
            // Folia: region-based scheduling
            try {
                val world = location.world ?: run {
                    Bukkit.getScheduler().runTask(plugin, task)
                    return
                }
                
                // Используем Folia API
                val scheduler = world.javaClass.getMethod("getScheduler").invoke(world)
                val runMethod = scheduler.javaClass.getMethod(
                    "run",
                    Plugin::class.java,
                    Location::class.java,
                    Consumer::class.java
                )
                
                runMethod.invoke(scheduler, plugin, location, Consumer<Any> { task.run() })
            } catch (e: Exception) {
                // Fallback на обычный scheduler
                Bukkit.getScheduler().runTask(plugin, task)
            }
        } else {
            // Paper: обычный scheduler
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }
    
    /**
     * Запустить задачу для entity (Folia-safe)
     */
    fun runAtEntity(
        plugin: Plugin,
        entity: Entity,
        task: Runnable
    ) {
        if (isFolia) {
            try {
                // Используем Folia entity scheduler
                val scheduler = entity.javaClass.getMethod("getScheduler").invoke(entity)
                val runMethod = scheduler.javaClass.getMethod(
                    "run",
                    Plugin::class.java,
                    Consumer::class.java,
                    Runnable::class.java
                )
                
                runMethod.invoke(scheduler, plugin, Consumer<Any> { task.run() }, null)
            } catch (e: Exception) {
                // Fallback
                Bukkit.getScheduler().runTask(plugin, task)
            }
        } else {
            // Paper: обычный scheduler
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }
    
    /**
     * Запустить задачу с задержкой в регионе
     */
    fun runAtLocationLater(
        plugin: Plugin,
        location: Location,
        delay: Long,
        task: Runnable
    ) {
        if (isFolia) {
            try {
                val world = location.world ?: run {
                    Bukkit.getScheduler().runTaskLater(plugin, task, delay)
                    return
                }
                
                val scheduler = world.javaClass.getMethod("getScheduler").invoke(world)
                val runMethod = scheduler.javaClass.getMethod(
                    "runDelayed",
                    Plugin::class.java,
                    Location::class.java,
                    Consumer::class.java,
                    Long::class.java
                )
                
                runMethod.invoke(scheduler, plugin, location, Consumer<Any> { task.run() }, delay)
            } catch (e: Exception) {
                Bukkit.getScheduler().runTaskLater(plugin, task, delay)
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay)
        }
    }
    
    /**
     * Запустить повторяющуюся задачу в регионе
     */
    fun runAtLocationTimer(
        plugin: Plugin,
        location: Location,
        delay: Long,
        period: Long,
        task: Runnable
    ) {
        if (isFolia) {
            try {
                val world = location.world ?: run {
                    Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period)
                    return
                }
                
                val scheduler = world.javaClass.getMethod("getScheduler").invoke(world)
                val runMethod = scheduler.javaClass.getMethod(
                    "runAtFixedRate",
                    Plugin::class.java,
                    Location::class.java,
                    Consumer::class.java,
                    Long::class.java,
                    Long::class.java
                )
                
                runMethod.invoke(scheduler, plugin, location, Consumer<Any> { task.run() }, delay, period)
            } catch (e: Exception) {
                Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period)
            }
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period)
        }
    }
    
    /**
     * Async задача (работает одинаково на Paper и Folia)
     */
    fun runAsync(plugin: Plugin, task: Runnable) {
        if (isFolia) {
            try {
                val asyncScheduler = Bukkit.getServer().javaClass
                    .getMethod("getAsyncScheduler")
                    .invoke(Bukkit.getServer())
                
                val runMethod = asyncScheduler.javaClass.getMethod(
                    "runNow",
                    Plugin::class.java,
                    Consumer::class.java
                )
                
                runMethod.invoke(asyncScheduler, plugin, Consumer<Any> { task.run() })
            } catch (e: Exception) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
        }
    }
    
    /**
     * Проверить запущен ли Folia
     */
    fun isFoliaServer(): Boolean = isFolia
    
    /**
     * Получить информацию о сервере
     */
    fun getServerInfo(): String {
        return if (isFolia) {
            "§aFolia §7(Multithreaded)"
        } else {
            "§ePaper §7(Single-threaded)"
        }
    }
}
