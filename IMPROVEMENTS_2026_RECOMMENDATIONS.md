# 🚀 Рекомендации по улучшению PvPKits - 2026

## Анализ трендов и best practices

На основе исследования актуальных трендов Minecraft PvP серверов и Paper API в 2026 году.

---

## 1. 🎯 Улучшения боевой механики (Combat)

### 1.1 Поддержка новых механик 1.21+
**Приоритет: ВЫСОКИЙ**

Minecraft 1.21 принес изменения в sprint механику и комбо-системы:

```kotlin
// Новая система комбо с учетом 1.21 sprint fix
class CombatMechanicsManager(private val plugin: PvPKitsPlugin) {
    
    /**
     * W-tapping detection с новой механикой спринта
     */
    fun detectWTap(player: Player): Boolean {
        // 1.21 изменил timing для W-tap
        // Нужна более точная детекция
    }
    
    /**
     * Combo counter с учетом новых механик
     */
    fun trackCombo(attacker: Player, victim: Player) {
        // Отслеживание комбо с новым sprint behavior
    }
    
    /**
     * Critical hit detection
     */
    fun isCriticalHit(player: Player): Boolean {
        return player.fallDistance > 0.0 && 
               !player.isOnGround && 
               !player.isInWater &&
               player.velocity.y < 0
    }
}
```

**Что добавить:**
- ✅ Детекция W-tapping (новая механика 1.21)
- ✅ Combo counter с визуальным отображением
- ✅ Critical hit tracking
- ✅ Sweep attack поддержка
- ✅ Shield blocking mechanics

---

## 2. 🎮 MMR Matchmaking система

### 2.1 Умный подбор противников
**Приоритет: ВЫСОКИЙ**

Современные PvP серверы используют MMR (Matchmaking Rating) для честных матчей:

```kotlin
class MatchmakingManager(private val plugin: PvPKitsPlugin) {
    
    private val queue = ConcurrentHashMap<String, MutableList<QueueEntry>>()
    
    data class QueueEntry(
        val uuid: UUID,
        val rating: Int,
        val queueTime: Long,
        val kitName: String
    )
    
    /**
     * Найти подходящего противника по MMR
     */
    fun findMatch(player: Player, kitName: String): Player? {
        val playerRating = plugin.ratingManager.getRating(player.uniqueId)
        val entries = queue[kitName] ?: return null
        
        // Расширяем диапазон поиска со временем
        val waitTime = System.currentTimeMillis() - entries.first().queueTime
        val ratingRange = calculateRatingRange(waitTime)
        
        return entries.find { entry ->
            val opponentRating = entry.rating
            abs(playerRating - opponentRating) <= ratingRange
        }?.let { plugin.server.getPlayer(it.uuid) }
    }
    
    /**
     * Диапазон рейтинга расширяется со временем ожидания
     */
    private fun calculateRatingRange(waitTimeMs: Long): Int {
        val baseRange = 100 // ±100 рейтинга
        val expansion = (waitTimeMs / 10000) * 50 // +50 каждые 10 секунд
        return (baseRange + expansion).coerceAtMost(500)
    }
    
    /**
     * Приоритетная очередь по времени ожидания
     */
    fun addToQueue(player: Player, kitName: String) {
        val rating = plugin.ratingManager.getRating(player.uniqueId)
        val entry = QueueEntry(
            uuid = player.uniqueId,
            rating = rating,
            queueTime = System.currentTimeMillis(),
            kitName = kitName
        )
        
        queue.getOrPut(kitName) { mutableListOf() }.add(entry)
        
        // Попытка найти матч
        findMatch(player, kitName)?.let { opponent ->
            startMatch(player, opponent, kitName)
        }
    }
}
```

**Преимущества:**
- Честные матчи (близкий уровень скилла)
- Меньше фрустрации для новичков
- Больше челленджа для профи
- Динамическое расширение диапазона поиска

---

## 3. ⚡ Folia поддержка (Multithreading)

### 3.1 Переход на Folia-совместимые schedulers
**Приоритет: СРЕДНИЙ**

Folia - это Paper fork с многопоточностью. Для больших серверов (200+ игроков) дает 5-10x прирост производительности.

```kotlin
// Уже используем SchedulerUtils - хорошо!
// Но можно улучшить для Folia

object FoliaSchedulerUtils {
    
    /**
     * Region-based task scheduling (Folia)
     */
    fun runAtLocation(
        plugin: Plugin,
        location: Location,
        task: Runnable
    ) {
        if (isFolia()) {
            // Folia: schedule at specific region
            location.world?.scheduler?.run(plugin, location) { task.run() }
        } else {
            // Paper: fallback to main thread
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }
    
    /**
     * Entity-based task scheduling (Folia)
     */
    fun runAtEntity(
        plugin: Plugin,
        entity: Entity,
        task: Consumer<ScheduledTask>
    ) {
        if (isFolia()) {
            entity.scheduler.run(plugin, task, null)
        } else {
            Bukkit.getScheduler().runTask(plugin) { 
                task.accept(null) 
            }
        }
    }
    
    private fun isFolia(): Boolean {
        return try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
}
```

**Что изменить:**
- ✅ Использовать region-based scheduling для арен
- ✅ Entity-based scheduling для игроков
- ✅ Async все операции с базой данных (уже есть!)
- ✅ Thread-safe все ConcurrentHashMap (уже есть!)

---

## 4. 📊 Расширенная аналитика

### 4.1 Heatmaps и визуализация
**Приоритет: СРЕДНИЙ**

```kotlin
class HeatmapManager(private val plugin: PvPKitsPlugin) {
    
    private val deathLocations = ConcurrentHashMap<String, MutableList<Location>>()
    private val killLocations = ConcurrentHashMap<String, MutableList<Location>>()
    
    /**
     * Записать локацию смерти
     */
    fun recordDeath(arenaName: String, location: Location) {
        deathLocations.getOrPut(arenaName) { mutableListOf() }.add(location)
    }
    
    /**
     * Получить heatmap для арены
     */
    fun getHeatmap(arenaName: String): Map<String, Int> {
        val locations = deathLocations[arenaName] ?: return emptyMap()
        
        // Группируем по чанкам
        return locations.groupingBy { 
            "${it.blockX / 16},${it.blockZ / 16}" 
        }.eachCount()
    }
    
    /**
     * Визуализация heatmap частицами
     */
    fun visualizeHeatmap(player: Player, arenaName: String) {
        val heatmap = getHeatmap(arenaName)
        
        heatmap.forEach { (chunk, deaths) ->
            val (x, z) = chunk.split(",").map { it.toInt() }
            val location = Location(
                player.world,
                x * 16.0 + 8,
                player.location.y,
                z * 16.0 + 8
            )
            
            // Интенсивность цвета зависит от количества смертей
            val color = when {
                deaths > 50 -> Color.RED
                deaths > 20 -> Color.ORANGE
                deaths > 10 -> Color.YELLOW
                else -> Color.GREEN
            }
            
            player.spawnParticle(
                Particle.DUST,
                location,
                10,
                DustOptions(color, 2.0f)
            )
        }
    }
}
```

---

## 5. 🎯 Anti-Cheat интеграция

### 5.1 Базовая детекция читов
**Приоритет: ВЫСОКИЙ**

```kotlin
class AntiCheatManager(private val plugin: PvPKitsPlugin) {
    
    private val clickData = ConcurrentHashMap<UUID, ClickTracker>()
    
    data class ClickTracker(
        val clicks: MutableList<Long> = mutableListOf(),
        var violations: Int = 0
    )
    
    /**
     * Детекция автокликера
     */
    fun checkAutoClicker(player: Player): Boolean {
        val tracker = clickData.getOrPut(player.uniqueId) { ClickTracker() }
        val now = System.currentTimeMillis()
        
        tracker.clicks.add(now)
        tracker.clicks.removeIf { now - it > 1000 } // Последняя секунда
        
        // Более 20 CPS = подозрительно
        if (tracker.clicks.size > 20) {
            tracker.violations++
            
            if (tracker.violations > 5) {
                // Кик или бан
                player.kick(Component.text("Suspected auto-clicker"))
                return true
            }
        }
        
        return false
    }
    
    /**
     * Детекция Reach (дальность атаки)
     */
    fun checkReach(attacker: Player, victim: Player): Boolean {
        val distance = attacker.location.distance(victim.location)
        val maxReach = 3.5 // Vanilla max reach
        
        if (distance > maxReach) {
            plugin.logger.warning(
                "${attacker.name} hit ${victim.name} from ${distance}m (max: $maxReach)"
            )
            return true
        }
        
        return false
    }
    
    /**
     * Детекция Velocity (игнорирование отбрасывания)
     */
    fun checkVelocity(player: Player, expectedVelocity: Vector) {
        // Сохраняем ожидаемую velocity
        // Проверяем через несколько тиков
    }
}
```

---

## 6. 🎨 Улучшенный UI/UX

### 6.1 Scoreboard с анимацией
**Приоритет: НИЗКИЙ**

```kotlin
class AnimatedScoreboard(private val plugin: PvPKitsPlugin) {
    
    private var frame = 0
    
    fun updateScoreboard(player: Player) {
        val scoreboard = player.scoreboard
        val objective = scoreboard.getObjective("pvpkits") 
            ?: scoreboard.registerNewObjective("pvpkits", "dummy", getAnimatedTitle())
        
        objective.displaySlot = DisplaySlot.SIDEBAR
        
        // Анимированный контент
        val lines = getAnimatedLines(player)
        lines.forEachIndexed { index, line ->
            objective.getScore(line).score = lines.size - index
        }
        
        frame++
    }
    
    private fun getAnimatedTitle(): Component {
        val colors = listOf("§c", "§6", "§e", "§a", "§b", "§d")
        val color = colors[frame % colors.size]
        return Component.text("${color}§lPVP KITS")
    }
    
    private fun getAnimatedLines(player: Player): List<String> {
        val rating = plugin.ratingManager.getRating(player.uniqueId)
        val rank = plugin.ratingManager.getRank(rating)
        
        return listOf(
            "§7§m                    ",
            "§fRating: §e$rating",
            "§fRank: ${rank.color}${rank.name}",
            "",
            "§fKills: §a${plugin.statsManager.getKills(player.uniqueId)}",
            "§fDeaths: §c${plugin.statsManager.getDeaths(player.uniqueId)}",
            "§fK/D: §e${plugin.statsManager.getKD(player.uniqueId)}",
            "",
            "§fQueue: §b${plugin.duelManager.getTotalInQueues()}",
            "§fMatches: §d${plugin.duelManager.getActiveMatchCount()}",
            "§7§m                    "
        )
    }
}
```

---

## 7. 🔧 Performance оптимизации

### 7.1 Кэширование с Caffeine
**Приоритет: СРЕДНИЙ**

```kotlin
// Уже используется в RatingManager - отлично!
// Можно расширить на другие системы

class CachedStatsManager(private val plugin: PvPKitsPlugin) {
    
    private val statsCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build<UUID, PlayerStats>()
    
    private val leaderboardCache = Caffeine.newBuilder()
        .maximumSize(1)
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build<String, List<LeaderboardEntry>>()
    
    fun getStats(uuid: UUID): PlayerStats {
        return statsCache.get(uuid) { 
            loadStatsFromDatabase(uuid) 
        }
    }
    
    fun getLeaderboard(type: String): List<LeaderboardEntry> {
        return leaderboardCache.get(type) {
            loadLeaderboardFromDatabase(type)
        }
    }
}
```

### 7.2 Batch операции для БД
**Приоритет: ВЫСОКИЙ**

```kotlin
class BatchStatsManager(private val plugin: PvPKitsPlugin) {
    
    private val pendingUpdates = ConcurrentLinkedQueue<StatUpdate>()
    
    init {
        // Flush каждые 30 секунд
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            flushUpdates()
        }, 600L, 600L)
    }
    
    fun queueUpdate(update: StatUpdate) {
        pendingUpdates.offer(update)
        
        // Flush если накопилось много
        if (pendingUpdates.size > 100) {
            flushUpdates()
        }
    }
    
    private fun flushUpdates() {
        if (pendingUpdates.isEmpty()) return
        
        val updates = mutableListOf<StatUpdate>()
        while (pendingUpdates.isNotEmpty()) {
            pendingUpdates.poll()?.let { updates.add(it) }
        }
        
        // Batch INSERT/UPDATE
        plugin.launch {
            CoroutineUtils.io {
                connection.use { conn ->
                    val stmt = conn.prepareStatement(
                        "INSERT OR REPLACE INTO stats VALUES (?, ?, ?, ?)"
                    )
                    
                    updates.forEach { update ->
                        stmt.setString(1, update.uuid.toString())
                        stmt.setInt(2, update.kills)
                        stmt.setInt(3, update.deaths)
                        stmt.setLong(4, update.timestamp)
                        stmt.addBatch()
                    }
                    
                    stmt.executeBatch()
                }
            }
        }
    }
}
```

---

## 8. 🌐 Web API для статистики

### 8.1 REST API endpoint
**Приоритет: НИЗКИЙ**

```kotlin
class WebAPIServer(private val plugin: PvPKitsPlugin) {
    
    private val server = embeddedServer(Netty, port = 8080) {
        routing {
            get("/api/player/{uuid}") {
                val uuid = UUID.fromString(call.parameters["uuid"])
                val stats = plugin.statsManager.getStats(uuid)
                val rating = plugin.ratingManager.getRating(uuid)
                
                call.respond(mapOf(
                    "uuid" to uuid.toString(),
                    "rating" to rating,
                    "kills" to stats.kills,
                    "deaths" to stats.deaths,
                    "kd" to stats.getKD()
                ))
            }
            
            get("/api/leaderboard") {
                val top = plugin.ratingManager.getTopPlayers(100)
                call.respond(top)
            }
            
            get("/api/matches/active") {
                val matches = plugin.duelManager.getActiveMatches()
                call.respond(matches)
            }
        }
    }
    
    fun start() {
        server.start(wait = false)
    }
}
```

---

## 9. 🎯 Приоритетный план внедрения

### Фаза 1: Критичные улучшения (1-2 недели)
1. ✅ MMR Matchmaking система
2. ✅ Anti-Cheat базовая детекция
3. ✅ Batch операции для БД
4. ✅ Combat mechanics (W-tap, combo counter)

### Фаза 2: Важные улучшения (2-3 недели)
5. ✅ Folia поддержка (schedulers)
6. ✅ Расширенное кэширование
7. ✅ Heatmaps и аналитика
8. ✅ Улучшенный scoreboard

### Фаза 3: Дополнительные фичи (1-2 недели)
9. ✅ Web API
10. ✅ Discord интеграция
11. ✅ Replay viewer GUI
12. ✅ Tournament brackets visualization

---

## 10. 📚 Рекомендуемые библиотеки

### Уже используются (отлично!):
- ✅ Kotlin 2.3.0
- ✅ Kotlin Coroutines
- ✅ HikariCP
- ✅ Caffeine Cache
- ✅ Adventure API

### Стоит добавить:
```xml
<!-- Anti-Cheat -->
<dependency>
    <groupId>com.github.retrooper</groupId>
    <artifactId>packetevents</artifactId>
    <version>2.0.0</version>
</dependency>

<!-- Web API -->
<dependency>
    <groupId>io.ktor</groupId>
    <artifactId>ktor-server-netty</artifactId>
    <version>2.3.7</version>
</dependency>

<!-- Discord Integration -->
<dependency>
    <groupId>net.dv8tion</groupId>
    <artifactId>JDA</artifactId>
    <version>5.0.0</version>
</dependency>

<!-- Metrics -->
<dependency>
    <groupId>org.bstats</groupId>
    <artifactId>bstats-bukkit</artifactId>
    <version>3.0.2</version>
</dependency>
```

---

## 11. 🎓 Best Practices 2026

### Code Quality
- ✅ Используйте Kotlin coroutines (уже есть!)
- ✅ Thread-safe коллекции (ConcurrentHashMap)
- ✅ Async все I/O операции
- ✅ Кэширование часто используемых данных
- ✅ Batch операции для БД

### Performance
- ✅ Paper API вместо Spigot
- ✅ Folia для больших серверов (200+ игроков)
- ✅ Region-based scheduling
- ✅ Lazy loading данных
- ✅ Оптимизация particle effects

### Security
- ✅ Базовый anti-cheat
- ✅ Rate limiting для команд
- ✅ Input validation
- ✅ SQL injection protection (PreparedStatement)
- ✅ Permission checks

---

## Заключение

Ваш проект уже использует современный стек (Kotlin 2.3, Coroutines, Paper 1.21.8). Основные улучшения:

1. **MMR Matchmaking** - для честных матчей
2. **Anti-Cheat** - базовая защита
3. **Folia support** - для масштабирования
4. **Heatmaps** - аналитика арен
5. **Web API** - интеграция с сайтом

Проект готов к 2026 году! 🚀
