package com.pvpkits.utils

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.util.concurrent.TimeUnit

/**
 * High-performance cache for MiniMessage components
 * Uses Caffeine with Window TinyLFU eviction policy
 * 
 * Best Practices 2026:
 * - Cache parsed components to avoid repeated parsing
 * - Use Caffeine's advanced eviction algorithms
 * - Monitor cache hit rates for optimization
 */
object ComponentCache {
    
    private val miniMessage = MiniMessage.miniMessage()
    
    /**
     * Cache for static components (no placeholders)
     * Larger size, longer expiration
     */
    private val staticCache: Cache<String, Component> = Caffeine.newBuilder()
        .maximumSize(1_000)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .recordStats()
        .build()
    
    /**
     * Cache for dynamic components (with placeholders)
     * Smaller size, shorter expiration
     */
    private val dynamicCache: Cache<String, Component> = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .recordStats()
        .build()
    
    /**
     * Parse a static MiniMessage string (no placeholders)
     * Results are cached for better performance
     */
    fun parseStatic(miniMessageString: String): Component {
        return staticCache.get(miniMessageString) {
            miniMessage.deserialize(miniMessageString)
        }!!
    }
    
    /**
     * Parse a dynamic MiniMessage string with tag resolvers
     * Results are cached with the full key (message + resolvers hash)
     */
    fun parseDynamic(miniMessageString: String, vararg resolvers: TagResolver): Component {
        // Create cache key from message and resolvers
        val cacheKey = "$miniMessageString:${resolvers.contentHashCode()}"
        
        return dynamicCache.get(cacheKey) {
            miniMessage.deserialize(miniMessageString, *resolvers)
        }!!
    }
    
    /**
     * Parse without caching (for one-time use)
     */
    fun parseUncached(miniMessageString: String, vararg resolvers: TagResolver): Component {
        return if (resolvers.isEmpty()) {
            miniMessage.deserialize(miniMessageString)
        } else {
            miniMessage.deserialize(miniMessageString, *resolvers)
        }
    }
    
    /**
     * Clear all caches
     */
    fun clearAll() {
        staticCache.invalidateAll()
        dynamicCache.invalidateAll()
    }
    
    /**
     * Get cache statistics for monitoring
     */
    fun getStats(): Map<String, Any> {
        val staticStats = staticCache.stats()
        val dynamicStats = dynamicCache.stats()
        
        return mapOf(
            "static_cache_size" to staticCache.estimatedSize(),
            "static_hit_rate" to staticStats.hitRate(),
            "static_miss_rate" to staticStats.missRate(),
            "static_eviction_count" to staticStats.evictionCount(),
            
            "dynamic_cache_size" to dynamicCache.estimatedSize(),
            "dynamic_hit_rate" to dynamicStats.hitRate(),
            "dynamic_miss_rate" to dynamicStats.missRate(),
            "dynamic_eviction_count" to dynamicStats.evictionCount()
        )
    }
    
    /**
     * Get cache hit rate (0.0 to 1.0)
     */
    fun getHitRate(): Double {
        val staticStats = staticCache.stats()
        val dynamicStats = dynamicCache.stats()
        
        val totalRequests = staticStats.requestCount() + dynamicStats.requestCount()
        if (totalRequests == 0L) return 0.0
        
        val totalHits = staticStats.hitCount() + dynamicStats.hitCount()
        return totalHits.toDouble() / totalRequests.toDouble()
    }
}
