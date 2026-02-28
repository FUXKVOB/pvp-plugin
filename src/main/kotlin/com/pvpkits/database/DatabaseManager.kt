package com.pvpkits.database

import com.pvpkits.PvPKitsPlugin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection

/**
 * Database Manager - 2026 Edition
 * 
 * Best Practices:
 * - HikariCP connection pooling
 * - SQLite WAL mode для производительности
 * - Оптимальная конфигурация для SQLite
 * - Batch operations support
 */
class DatabaseManager(private val plugin: PvPKitsPlugin) {
    
    private lateinit var dataSource: HikariDataSource
    
    /**
     * Инициализация базы данных с оптимальной конфигурацией
     */
    fun initialize() {
        val dbFile = plugin.dataFolder.resolve("pvpkits.db")
        
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
            driverClassName = "org.sqlite.JDBC"
            
            // SQLite = single writer, поэтому 1 connection
            maximumPoolSize = 1
            minimumIdle = 1
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            
            // SQLite оптимизации
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            
            // WAL mode для лучшей производительности (2026 стандарт)
            connectionInitSql = """
                PRAGMA journal_mode=WAL;
                PRAGMA synchronous=NORMAL;
                PRAGMA cache_size=10000;
                PRAGMA temp_store=MEMORY;
                PRAGMA mmap_size=30000000000;
            """.trimIndent()
            
            poolName = "PvPKits-Pool"
        }
        
        dataSource = HikariDataSource(config)
        
        // Создать таблицы
        createTables()
        
        plugin.logger.info("Database initialized with WAL mode and optimized settings")
    }
    
    /**
     * Создать таблицы
     */
    private fun createTables() {
        executeSync { connection ->
            connection.createStatement().use { statement ->
                // Player stats table
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        uuid TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL,
                        kills INTEGER DEFAULT 0,
                        deaths INTEGER DEFAULT 0,
                        current_killstreak INTEGER DEFAULT 0,
                        best_killstreak INTEGER DEFAULT 0,
                        last_kit_used TEXT,
                        last_updated INTEGER
                    )
                """)
                
                // Kit usage table
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS kit_usage (
                        player_uuid TEXT,
                        kit_name TEXT,
                        use_count INTEGER DEFAULT 0,
                        PRIMARY KEY (player_uuid, kit_name),
                        FOREIGN KEY (player_uuid) REFERENCES player_stats(uuid)
                    )
                """)
                
                // Indexes для производительности
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_kills ON player_stats(kills DESC)
                """)
                
                statement.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS idx_best_killstreak ON player_stats(best_killstreak DESC)
                """)
            }
        }
    }
    
    /**
     * Получить connection из пула
     */
    fun getConnection(): Connection {
        return dataSource.connection
    }
    
    /**
     * Выполнить синхронную операцию
     */
    fun <T> executeSync(block: (Connection) -> T): T? {
        return try {
            getConnection().use { connection ->
                block(connection)
            }
        } catch (e: Exception) {
            plugin.logger.severe("Database error: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Выполнить batch операцию
     */
    fun executeBatch(block: (Connection) -> Unit) {
        try {
            getConnection().use { connection ->
                connection.autoCommit = false
                try {
                    block(connection)
                    connection.commit()
                } catch (e: Exception) {
                    connection.rollback()
                    throw e
                } finally {
                    connection.autoCommit = true
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Batch operation error: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Получить статистику пула
     */
    fun getPoolStats(): Map<String, Any> {
        return mapOf(
            "active_connections" to dataSource.hikariPoolMXBean.activeConnections,
            "idle_connections" to dataSource.hikariPoolMXBean.idleConnections,
            "total_connections" to dataSource.hikariPoolMXBean.totalConnections,
            "threads_awaiting" to dataSource.hikariPoolMXBean.threadsAwaitingConnection
        )
    }
    
    /**
     * Shutdown пула
     */
    fun shutdown() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
            plugin.logger.info("Database connection pool closed")
        }
    }
}
