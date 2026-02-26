# PvPKits Plugin - Полная документация

## 📦 Сборка и установка

### Требования
- Java 21+
- Maven 3.8+

### Сборка плагина

```bash
# Windows
mvnw.cmd clean package

# Linux/Mac
./mvnw clean package
```

Готовый плагин: `target/PvPKits-1.0.0.jar`

### Установка
1. Скопируйте jar в папку `plugins/`
2. Перезапустите сервер
3. Настройте `plugins/PvPKits/config.yml`

---

## 🌍 Настройка миров

### Проблема: Плагин генерирует новые миры

Если у вас миры в папках типа `D:\server\lobby\helloween`, плагин может генерировать новые миры.

### Решение 1: Переименовать папки (рекомендуется)

```bash
# Windows
move D:\server\lobby\helloween D:\server\helloween_lobby
rmdir D:\server\lobby

# Linux/Mac
mv /server/lobby/helloween /server/helloween_lobby
rmdir /server/lobby
```

### Решение 2: Настроить config.yml

```yaml
lobby:
  spawn:
    world: helloween  # Имя МИРА из level.dat, не папки!
    x: 0
    y: 64
    z: 0
```

Плагин автоматически найдет мир в подпапках.

### Поддерживаемые структуры

✅ **Прямая структура:**
```
server/
├── helloween_lobby/
│   ├── level.dat
│   └── region/
└── plugins/
```

✅ **Вложенная структура:**
```
server/
├── lobby/
│   └── helloween/
│       ├── level.dat
│       └── region/
└── plugins/
```

### Проверка загрузки

Смотрите логи при запуске:
```
[PvPKits] Loading world: helloween
[PvPKits] ✓ Successfully loaded world: helloween
[PvPKits]   - Lobby: helloween
```

---

## ⚙️ Конфигурация

### config.yml

```yaml
# Настройки лобби
lobby:
  teleport-on-join: true
  spawn:
    world: lobby        # Имя мира
    x: 0
    y: 64
    z: 0
    yaw: 0
    pitch: 0

# Неймтеги над игроками
nametag:
  enabled: true
  format: "<gray>{name}\n{health} {ping}"
  update-interval: 10  # тиков (10 = 0.5 сек)

# Статистика
stats:
  enabled: true
  show-kill-message: true
  gui-leaderboard: true
  tracked-worlds: []  # Пусто = все миры
  cleanup-on-quit: false

# GUI настройки
gui:
  title: "<gradient:#ff0000:#ff6b6b>⚔ PvP Kits</gradient>"
  rows: 6
  items-per-page: 28
  enable-sounds: true
  enable-particles: true

# Кулдаун китов (секунды)
kit-cooldown: 60

# Очищать инвентарь при выдаче кита
clear-inventory: true

# Звуки
sounds:
  kit-select: "ENTITY_PLAYER_LEVELUP"
  kit-cooldown: "ENTITY_VILLAGER_NO"
  gui-click: "UI_BUTTON_CLICK"
  page-turn: "ITEM_BOOK_PAGE_TURN"

# Сообщения (поддержка MiniMessage)
messages:
  prefix: "<dark_gray>[<red>⚔</red>]</dark_gray> "
  kit-received: "<gradient:#00ff00:#00aa00>✓</gradient> <green>You received the {kit} kit!"
  kit-cooldown: "<red>⏱ Cooldown: <yellow>{time}"
  kit-not-found: "<red>✗ Kit not found!"
  no-permission: "<red>✗ No permission!"
  
  # Киллстрики
  killstreak.5: "&6&l⚔ &e{0} &6is on a &e5 kill streak!"
  killstreak.10: "&c&l⚔ &e{0} &cis on a &e10 kill streak! &lUNSTOPPABLE!"
  killstreak.15: "&4&l⚔ &e{0} &4is on a &e15 kill streak! &lGODLIKE!"
  killstreak.20: "&d&l⚔ &e{0} &dis on a &e20 kill streak! &lLEGENDARY!"
```

### kits.yml

```yaml
kits:
  crystal:
    display-name: "<gradient:#00ffff:#ff00ff>⚡ Crystal PvP</gradient>"
    icon: "END_CRYSTAL"
    permission: "pvpkits.kit.crystal"
    cooldown: 60
    description:
      - "&7Взрывной стиль боя"
      - "&e⚔ Difficulty: &cHard"
      - "&e⚡ Style: &fExplosive"
    items:
      - "NETHERITE_HELMET{Enchantments:[{id:protection,lvl:4},{id:unbreaking,lvl:3}]}"
      - "NETHERITE_CHESTPLATE{Enchantments:[{id:protection,lvl:4}]}"
      - "NETHERITE_LEGGINGS{Enchantments:[{id:protection,lvl:4}]}"
      - "NETHERITE_BOOTS{Enchantments:[{id:protection,lvl:4}]}"
      - "NETHERITE_SWORD{Enchantments:[{id:sharpness,lvl:5}]}"
      - "END_CRYSTAL 16"
      - "OBSIDIAN 64"
      - "TOTEM_OF_UNDYING 3"
      - "GOLDEN_APPLE 16"
```

### Создание своего кита

```yaml
kits:
  mykit:
    display-name: "<gradient:#00ff00:#00aa00>My Custom Kit</gradient>"
    icon: "DIAMOND_SWORD"
    permission: "pvpkits.kit.mykit"
    cooldown: 30
    description:
      - "&7My awesome kit"
      - "&e⚔ Difficulty: &aEasy"
    items:
      - "DIAMOND_SWORD{Enchantments:[{id:sharpness,lvl:5}]}"
      - "DIAMOND_HELMET{Enchantments:[{id:protection,lvl:4}]}"
      - "DIAMOND_CHESTPLATE{Enchantments:[{id:protection,lvl:4}]}"
      - "DIAMOND_LEGGINGS{Enchantments:[{id:protection,lvl:4}]}"
      - "DIAMOND_BOOTS{Enchantments:[{id:protection,lvl:4}]}"
      - "BOW{Enchantments:[{id:power,lvl:5}]}"
      - "ARROW 64"
      - "GOLDEN_APPLE 8"
```

---

## 🎮 Команды

### Киты
- `/kit` - Открыть меню китов
- `/kit <название>` - Получить кит напрямую
- `/createkit <название>` - Создать кит из инвентаря (админ)
- `/deletekit <название>` - Удалить кит (админ)

### Статистика
- `/stats [игрок]` - Посмотреть статистику
- `/top [kills|kd|streak]` - Топ игроков

### Арены
- `/arena create <название>` - Создать арену
- `/arena delete <название>` - Удалить арену
- `/arena list` - Список арен
- `/join [арена]` - Войти в арену
- `/leave` - Выйти из арены
- `/queue` - Войти в очередь
- `/arenas` - Список всех арен

### Дуэли
- `/duel queue <кит>` - Войти в очередь дуэлей
- `/duel leave` - Выйти из очереди
- `/duel stats` - Статистика дуэлей
- `/duelqueue <кит>` - Быстрый вход в очередь

---

## 🔑 Права доступа

### Базовые
- `pvpkits.use` - Использование китов (по умолчанию: true)
- `pvpkits.admin` - Админ команды (по умолчанию: op)
- `pvpkits.stats.others` - Просмотр чужой статистики (по умолчанию: op)

### Киты
- `pvpkits.kit.crystal` - Доступ к Crystal киту
- `pvpkits.kit.mace` - Доступ к Mace киту
- `pvpkits.kit.sword` - Доступ к Sword киту
- `pvpkits.kit.axe` - Доступ к Axe киту
- `pvpkits.kit.uhc` - Доступ к UHC киту
- `pvpkits.kit.potion` - Доступ к Potion киту
- `pvpkits.kit.archer` - Доступ к Archer киту
- `pvpkits.kit.tank` - Доступ к Tank киту

---

## 🎨 MiniMessage форматирование

### Цвета

```yaml
# Именованные цвета
"<red>Красный текст</red>"
"<green>Зеленый</green> <blue>Синий</blue>"

# RGB цвета
"<#FF5733>Оранжевый текст"
"<#00FF00>Зеленый RGB"

# Градиенты
"<gradient:#ff0000:#00ff00>Плавный переход</gradient>"
"<gradient:#ff0000:#ff6b6b>⚔ PvP Kits</gradient>"

# Радуга
"<rainbow>Радужный текст!</rainbow>"
```

### Форматирование

```yaml
# Жирный
"<bold>Жирный текст</bold>"

# Курсив
"<italic>Курсивный текст</italic>"

# Подчеркнутый
"<underlined>Подчеркнутый</underlined>"

# Зачеркнутый
"<strikethrough>Зачеркнутый</strikethrough>"

# Комбинация
"<bold><red>Жирный красный</red></bold>"
"<gradient:#ff0000:#00ff00><bold>Жирный градиент</bold></gradient>"
```

### Примеры для китов

```yaml
kits:
  mykit:
    display-name: "<gradient:#00ffff:#ff00ff><bold>⚡ Epic Kit</bold></gradient>"
    description:
      - "<gray>Легендарный кит"
      - "<gradient:#ffd700:#ffaa00>⭐ Legendary Tier</gradient>"
      - "<red>❤</red> <green>High Damage</green>"
```

---

## 📊 Best Practices 2026

### 1. HikariCP Connection Pool

Оптимальная конфигурация для SQLite:

```kotlin
// Автоматически настроено в плагине
maximumPoolSize = 3  // Оптимально для SQLite
minimumIdle = 1
connectionTimeout = 30_000
idleTimeout = 600_000
maxLifetime = 1_800_000
leakDetectionThreshold = 60_000
```

**Почему 3 соединения?**
- SQLite не поддерживает много одновременных записей
- Формула: `(CPU cores × 2) + 1`, но для SQLite оптимально 1-3

### 2. Caffeine Cache

Плагин использует продвинутое кэширование:

**Component Cache:**
- Статические компоненты: 1000 items, 30 min
- Динамические компоненты: 500 items, 5 min
- Hit rate: ~80-90%

**Leaderboard Cache:**
- 10 queries, 1 min expiration
- Hit rate: ~60-80%
- Автоматическая инвалидация при изменениях

**Item Cache (GUI):**
- 1000 items, 5 min expiration
- Lazy loading (только текущая страница)
- Hit rate: ~80%

### 3. Kotlin Coroutines

Асинхронные операции:

```kotlin
// File I/O
withContext(Dispatchers.IO) {
    kitsConfig.save(kitsFile)
}

// Database operations
withContext(Dispatchers.IO) {
    database.executeQuery()
}
```

**Structured Concurrency:**
- Все корутины привязаны к plugin scope
- Автоматическая отмена при shutdown
- Proper exception handling

### 4. Memory Management

**Cleanup on player quit:**
- Kit cooldowns
- GUI cache
- Stats cache
- Arena data
- Duel data

**Batch operations:**
```kotlin
// ❌ BAD: N queries
players.forEach { uuid ->
    database.updateStats(uuid)
}

// ✅ GOOD: 1 batch query
database.batchUpdateStats(players)
```

### 5. Performance Metrics

**Ожидаемые показатели:**
- Component cache hit rate: > 70%
- Leaderboard cache hit rate: > 60%
- Item cache hit rate: > 80%
- DB pool active connections: < 2
- Memory usage: стабильный

---

## 🐛 Решение проблем

### Проблема: "Lobby world not found"

**Причина:** Плагин не может найти мир лобби

**Решение:**
1. Проверьте структуру папок
2. Убедитесь что `level.dat` существует
3. Проверьте логи на ошибки
4. Попробуйте переименовать папку мира

### Проблема: Генерируется новый мир

**Причина:** Bukkit не находит существующий мир

**Решение:**
1. Используйте прямую структуру папок (без вложенности)
2. Проверьте `config.yml` - укажите правильное имя мира
3. Удалите новосозданный мир и перезапустите
4. Убедитесь что мир НЕ загружен другим плагином

### Проблема: "Invalid world (no level.dat)"

**Причина:** Папка не содержит валидный мир

**Решение:**
1. Проверьте что `level.dat` существует
2. Проверьте что файл не поврежден
3. Попробуйте скопировать мир заново

### Проблема: Низкая производительность

**Решение:**
1. Проверьте cache hit rates в логах
2. Убедитесь что используется Java 21+
3. Выделите больше RAM серверу
4. Проверьте количество активных connections в БД

### Проблема: Memory leak

**Решение:**
1. Убедитесь что используете последнюю версию
2. Проверьте что cleanup выполняется при quit
3. Мониторьте memory usage
4. Используйте leak detection в HikariCP

---

## 📈 Мониторинг

### Логи при запуске

```
[PvPKits] Database initialized with HikariCP pool (max: 3, min: 1)
[PvPKits] Loaded 8 kits
[PvPKits] Loading worlds from: D:\server
[PvPKits] ✓ Successfully loaded world: helloween
[PvPKits] World loading complete:
[PvPKits]   - Lobby: helloween
[PvPKits]   - Arenas: 2
[PvPKits] ╔════════════════════════════════════╗
[PvPKits] ║   PvPKits v2.1.0 Enabled          ║
[PvPKits] ║   Loaded 8 kits                   ║
[PvPKits] ║   Players tracked: 0              ║
[PvPKits] ║   Arenas: 2                       ║
[PvPKits] ║   Nametags: ON                    ║
[PvPKits] ║   Stats: ON                       ║
[PvPKits] ║   Java: 21                        ║
[PvPKits] ║   Kotlin 2.3.0 + Coroutines       ║
[PvPKits] ╚════════════════════════════════════╝
```

### Метрики для отслеживания

1. **Cache Hit Rates** - должны быть > 70%
2. **DB Pool Connections** - должно быть < 2 active
3. **Memory Usage** - должен быть стабильным
4. **TPS** - должен быть 20.0
5. **Player Count** - количество онлайн игроков

---

## 🔧 Технические детали

### Архитектура

```
PvPKitsPlugin (main)
├── KitManager - управление китами
├── StatsManager - статистика игроков
├── ArenaManager - управление аренами
├── DuelManager - система дуэлей
├── WorldManager - загрузка миров
├── NametagManager - неймтеги
├── ScoreboardManager - скорборды
├── DatabaseManager - работа с БД
└── GUI - интерфейсы
```

### Используемые библиотеки

- **Kotlin 2.3.0** - язык программирования
- **Java 21** - JVM
- **Paper API 1.21** - серверный API
- **Adventure API 4.17** - текстовые компоненты
- **MiniMessage 4.17** - форматирование
- **Kotlin Coroutines 1.9** - асинхронность
- **MCCoroutine 2.20** - интеграция корутин
- **HikariCP 6.2** - connection pooling
- **Caffeine 3.1.8** - кэширование
- **SQLite 3.48** - база данных

### Структура БД

**player_stats:**
- uuid (TEXT PRIMARY KEY)
- player_name (TEXT)
- kills (INTEGER)
- deaths (INTEGER)
- current_killstreak (INTEGER)
- best_killstreak (INTEGER)
- last_kit_used (TEXT)
- last_updated (INTEGER)

**kit_usage:**
- id (INTEGER PRIMARY KEY)
- player_uuid (TEXT)
- kit_name (TEXT)
- use_count (INTEGER)

---

## 📄 Changelog

### v2.1.0 (2026) - Performance Update
- 🔧 FIXED: Загрузка миров из вложенных папок
- ⚡ NEW: ComponentCache для MiniMessage (~80% hit rate)
- ⚡ NEW: Leaderboard caching (~70% hit rate)
- 🎯 IMPROVED: HikariCP оптимизирован для SQLite (10 → 3)
- 🎯 IMPROVED: GUI performance с lazy loading
- 🐛 FIXED: Memory leaks prevention
- 🐛 FIXED: Proper resource cleanup

### v2.0.0 (2026) - Modern Edition
- ✨ MiniMessage поддержка
- ⚡ Kotlin Coroutines
- 🚀 Java 21 support
- 🔧 Kotlin 2.3
- 📦 MCCoroutine интеграция
- 🎨 Adventure API
- 📊 SQLite статистика
- 🏟️ Система арен и дуэлей

---

## 📋 Требования

- **Minecraft:** 1.21+
- **Сервер:** Paper/Spigot
- **Java:** 21+
- **RAM:** Минимум 2GB

---

## 📄 Лицензия

MIT License - свободное использование и модификация

---

**Made with ❤️ using Kotlin 2.3 & Java 21**
