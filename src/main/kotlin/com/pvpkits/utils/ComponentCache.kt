package com.pvpkits.utils

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.util.concurrent.TimeUnit

/**
 * Component Cache - оптимизация MiniMessage парсинга
 * 
 * Best Practices 2026:
 * - Кэширование часто используемых компонентов
 * - Избегание повторного парсинга
 * - Caffeine cache для автоматической очистки
 */
object ComponentCache {
    
    private val miniMessage = MiniMessage.miniMessage()
    
    // Кэш для статических компонентов (без плейсхолдеров)
    private val staticCache: Cache<String, Component> = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .recordStats()
        .build()
    
    /**
     * Парсинг статического текста (кэшируется)
     */
    fun parse(text: String): Component {
        return staticCache.get(text) { 
            miniMessage.deserialize(it) 
        }!!
    }
    
    /**
     * Парсинг с плейсхолдерами (не кэшируется)
     */
    fun parseDynamic(text: String, vararg resolvers: TagResolver): Component {
        return miniMessage.deserialize(text, *resolvers)
    }
    
    /**
     * Парсинг с простыми плейсхолдерами (Map)
     */
    fun parseWithPlaceholders(text: String, placeholders: Map<String, String>): Component {
        var result = text
        placeholders.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        return miniMessage.deserialize(result)
    }
    
    /**
     * Очистить кэш
     */
    fun clearCache() {
        staticCache.invalidateAll()
    }
    
    /**
     * Получить статистику кэша
     */
    fun getCacheStats(): Map<String, Any> {
        val stats = staticCache.stats()
        return mapOf(
            "size" to staticCache.estimatedSize(),
            "hit_rate" to stats.hitRate(),
            "miss_rate" to stats.missRate(),
            "load_count" to stats.loadCount(),
            "eviction_count" to stats.evictionCount()
        )
    }
}
