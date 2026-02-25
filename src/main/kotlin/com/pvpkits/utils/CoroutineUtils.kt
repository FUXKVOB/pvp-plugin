package com.pvpkits.utils

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

/**
 * Utility for running async tasks with Kotlin Coroutines
 * Optimized for Paper servers with proper dispatcher management
 */
object CoroutineUtils {
    
    /**
     * IO Dispatcher - for database operations, file I/O, network calls
     */
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    
    /**
     * Default Dispatcher - for CPU-intensive operations
     */
    val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default
    
    /**
     * Plugin scope with SupervisorJob for structured concurrency
     * Children failures won't cancel siblings
     */
    private var _pluginScope: CoroutineScope? = null
    
    /**
     * Get or create the plugin coroutine scope
     * Must be initialized with initPluginScope()
     */
    val pluginScope: CoroutineScope
        get() = _pluginScope ?: throw IllegalStateException("CoroutineUtils not initialized. Call initPluginScope() first!")
    
    /**
     * Initialize the plugin scope - call in onEnable
     */
    fun initPluginScope(plugin: Plugin) {
        if (_pluginScope != null) {
            plugin.logger.warning("CoroutineUtils already initialized, cancelling existing scope")
            cancelPluginScope()
        }
        _pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        plugin.logger.info("CoroutineUtils initialized with structured concurrency")
    }
    
    /**
     * Cancel all coroutines - call in onDisable to prevent memory leaks
     */
    fun cancelPluginScope() {
        _pluginScope?.cancel("Plugin disabling")
        _pluginScope = null
    }
    
    /**
     * Get the main thread dispatcher for Bukkit API calls
     */
    fun mainDispatcher(plugin: Plugin): CoroutineContext {
        return plugin.minecraftDispatcher
    }
    
    /**
     * Run task asynchronously (off main thread) - for I/O operations
     * Use for: database, file operations, network calls
     */
    suspend fun <T> io(block: suspend () -> T): T {
        return withContext(ioDispatcher) {
            block()
        }
    }
    
    /**
     * Run task on CPU dispatcher - for CPU-intensive operations
     * Use for: parsing, calculations, data processing
     */
    suspend fun <T> cpu(block: suspend () -> T): T {
        return withContext(cpuDispatcher) {
            block()
        }
    }
    
    /**
     * Run task on main thread - for Bukkit API calls that require main thread
     */
    suspend fun <T> main(plugin: Plugin, block: suspend () -> T): T {
        return withContext(plugin.minecraftDispatcher) {
            block()
        }
    }
    
    /**
     * @deprecated Use io() instead
     */
    @Deprecated("Use io() for I/O operations", ReplaceWith("io(block)"))
    suspend fun <T> async(block: suspend () -> T): T {
        return io(block)
    }
    
    /**
     * @deprecated Use main() instead
     */
    @Deprecated("Use main(plugin, block) for main thread operations", ReplaceWith("main(plugin, block)"))
    suspend fun <T> sync(plugin: Plugin, block: suspend () -> T): T {
        return main(plugin, block)
    }
}
