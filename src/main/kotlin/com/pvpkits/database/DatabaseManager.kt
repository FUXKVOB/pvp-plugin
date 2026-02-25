package com.pvpkits.database

import com.pvpkits.PvPKitsPlugin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * Database manager using SQLite with HikariCP connection pooling
 * Provides high-performance async database operations
 */
class DatabaseManager(private val plugin: PvPKitsPlugin) {
    
    private var dataSource: HikariDataSource? = null
    private val executor = Executors.newFixedThreadPool(4)
    
    companion object {
        private const val DATABASE_NAME = "pvpkits.db"
    }
    
    /**
     * Initialize the database connection pool
     */
    fun initialize() {
        val dbFile = File(plugin.dataFolder, DATABASE_NAME)
        
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 300000 // 5 minutes
            connectionTimeout = 30000 // 30 seconds
            maxLifetime = 1800000 // 30 minutes
            
            // SQLite optimizations
            addDataSourceProperty("journal_mode", "WAL")
            addDataSourceProperty("synchronous", "NORMAL")
            addDataSourceProperty("cache_size", "10000")
            addDataSourceProperty("temp_store", "MEMORY")
            addDataSourceProperty("locking_mode", "NORMAL")
        }
        
        dataSource = HikariDataSource(config)
        
        // Create tables
        createTables()
        
        plugin.logger.info("Database initialized with connection pool (max: ${config.maximumPoolSize})")
    }
    
    /**
     * Create necessary tables
     */
    private fun createTables() {
        executeSync { connection ->
            // Player stats table
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    kills INTEGER DEFAULT 0,
                    deaths INTEGER DEFAULT 0,
                    current_killstreak INTEGER DEFAULT 0,
                    best_killstreak INTEGER DEFAULT 0,
                    last_kit_used TEXT,
                    last_updated INTEGER DEFAULT 0
                )
            """.trimIndent())
            
            // Kit usage table
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS kit_usage (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    kit_name TEXT NOT NULL,
                    use_count INTEGER DEFAULT 1,
                    FOREIGN KEY (player_uuid) REFERENCES player_stats(uuid),
                    UNIQUE(player_uuid, kit_name)
                )
            """.trimIndent())
            
            // Create indexes for better performance
            connection.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_stats_kills ON player_stats(kills DESC)")
            connection.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_stats_name ON player_stats(player_name)")
            connection.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_kit_usage_player ON kit_usage(player_uuid)")
        }
    }
    
    /**
     * Get a connection from the pool
     */
    fun getConnection(): Connection? {
        return try {
            dataSource?.connection
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to get database connection: ${e.message}")
            null
        }
    }
    
    /**
     * Execute a database operation asynchronously
     */
    fun <T> executeAsync(operation: (Connection) -> T): CompletableFuture<T> {
        return CompletableFuture.supplyAsync({
            getConnection()?.use { connection ->
                operation(connection)
            } ?: throw SQLException("No database connection available")
        }, executor)
    }
    
    /**
     * Execute a database operation synchronously
     */
    fun <T> executeSync(operation: (Connection) -> T): T? {
        return getConnection()?.use { connection ->
            operation(connection)
        }
    }
    
    /**
     * Execute a batch operation for better performance
     */
    fun executeBatch(operation: (Connection) -> Unit) {
        getConnection()?.use { connection ->
            connection.autoCommit = false
            try {
                operation(connection)
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }
    
    /**
     * Close the database connection pool
     */
    fun shutdown() {
        executor.shutdown()
        dataSource?.close()
        plugin.logger.info("Database connection pool closed")
    }
    
    /**
     * Get pool statistics for monitoring
     */
    fun getPoolStats(): Map<String, Any> {
        val pool = dataSource?.hikariPoolMXBean ?: return emptyMap()
        return mapOf(
            "active_connections" to pool.activeConnections,
            "idle_connections" to pool.idleConnections,
            "total_connections" to pool.totalConnections,
            "waiting_threads" to pool.threadsAwaitingConnection
        )
    }
}
