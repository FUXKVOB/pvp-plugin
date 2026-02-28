# 🚀 PvPKits v2.2 - Полное руководство 2026

## Обзор всех систем

### Основные системы
1. **9 готовых китов** - Crystal, Mace, Sword, Axe, UHC, Potion, Archer, Tank, Cart
2. **Арены и дуэли** - автоматический matchmaking
3. **Турниры** - single/double elimination brackets
4. **Spectator mode** - наблюдение за матчами

### Новые системы 2026
5. **ELO/MMR рейтинг** - 9 рангов от Unranked до Legend
6. **Replay система** - запись и сохранение матчей
7. **Cosmetics** - kill effects, death animations, trails, victory poses
8. **Duel Challenge система** - вызовы на дуэль между игроками
9. **Улучшенная статистика** - детальная аналитика по китам
10. **Улучшенная система арен** - шаблоны, инстансы, автосброс

---

## 1. 🏆 ELO/MMR Рейтинговая система

### Возможности
- ✅ Стандартный ELO алгоритм (K-factor 32/40)
- ✅ 9 рангов: Unranked → Legend
- ✅ Автоматический расчет после каждого матча
- ✅ Leaderboard с топ-10
- ✅ Win/Loss tracking
- ✅ Win streak система

### Команды
```bash
/rating [игрок]      # Посмотреть рейтинг
/elo [игрок]         # Алиас для /rating
/leaderboard         # Топ-10 игроков
/lb                  # Алиас для /leaderboard
```

### Ранги
| Ранг | Минимальный рейтинг | Цвет |
|------|---------------------|------|
| Unranked | 0 | §7 Серый |
| Bronze | 800 | §6 Золотой |
| Silver | 1000 | §f Белый |
| Gold | 1200 | §e Желтый |
| Platinum | 1400 | §b Голубой |
| Diamond | 1600 | §3 Темно-голубой |
| Master | 1800 | §5 Фиолетовый |
| Grandmaster | 2000 | §d Розовый |
| Legend | 2200 | §c Красный |

### Как работает
```kotlin
// После каждого матча
winner: 1000 ELO vs loser: 1000 ELO
→ Winner: +16 ELO (1016)
→ Loser: -16 ELO (984)

// Новые игроки (<30 матчей) получают K-factor 40
// Опытные игроки получают K-factor 32
```

### База данных
```sql
CREATE TABLE elo_ratings (
    uuid TEXT PRIMARY KEY,
    rating INTEGER DEFAULT 1000,
    wins INTEGER DEFAULT 0,
    losses INTEGER DEFAULT 0,
    win_streak INTEGER DEFAULT 0,
    best_win_streak INTEGER DEFAULT 0,
    rank TEXT DEFAULT 'UNRANKED',
    last_updated INTEGER
)
```

---

## 2. 📹 Replay система

### Возможности
- ✅ Автоматическая запись всех матчей
- ✅ Сохранение последних 10 реплеев на игрока
- ✅ Запись позиций, здоровья, событий
- ✅ Сжатие и оптимизация хранения
- ✅ Async сохранение на диск

### Что записывается
- Позиции обоих игроков (каждый тик)
- Здоровье игроков
- Yaw/Pitch (направление взгляда)
- События: удары, смерти, использование предметов
- Длительность матча
- Победитель

### Структура данных
```kotlin
data class ReplayData(
    val id: String,
    val player1: UUID,
    val player2: UUID,
    val kitName: String,
    val winner: UUID,
    val duration: Long,
    val frames: List<ReplayFrame>
)

data class ReplayFrame(
    val timestamp: Long,
    val player1Location: Location,
    val player2Location: Location,
    val player1Health: Double,
    val player2Health: Double,
    val events: List<ReplayEvent>
)
```

### Использование
```kotlin
// Автоматически при старте матча
val recorder = replayManager.startRecording(matchId, player1, player2, kitName)

// Запись фрейма (каждый тик)
recorder.recordFrame(p1Loc, p2Loc, p1Health, p2Health, ...)

// Завершение записи
replayManager.stopRecording(matchId, winner)

// Загрузка реплея
val replay = replayManager.loadReplay(replayId)
```

### Хранение
- Папка: `plugins/PvPKits/replays/`
- Формат: `{matchId}.replay` (сериализованный объект)
- Автоочистка: хранятся последние 10 реплеев на игрока

---

## 3. ✨ Cosmetics система

### Типы косметики

#### Kill Effects (эффекты при убийстве)
- ⚡ **Lightning** - удар молнии
- 💥 **Explosion** - взрыв
- 🩸 **Blood** - брызги крови
- 🎆 **Firework** - фейерверк
- ❤ **Hearts** - сердечки

#### Death Animations (анимации смерти)
- 👻 **Soul Escape** - вылет души
- 💨 **Smoke Poof** - облако дыма
- 🔥 **Flame Burst** - вспышка огня
- 🌀 **Ender Teleport** - эндер телепорт

#### Trail Effects (следы)
- 🌈 **Rainbow** - радужный след
- 🔥 **Fire** - огненный след
- ✨ **Sparkle** - искры
- ☁ **Cloud** - облака

#### Victory Poses (позы победы)
- 🏆 **Champion** - чемпионская поза
- 🎆 **Fireworks** - салют
- ⚡ **Lightning** - молния

### Команды
```bash
/cosmetics                    # Главное меню
/cosmetics kill <effect>      # Установить kill effect
/cosmetics death <animation>  # Установить death animation
/cosmetics trail <effect>     # Установить trail
/cosmetics victory <pose>     # Установить victory pose
/cosmetics clear <type|all>   # Очистить косметику
```

### Примеры
```bash
/cosmetics kill lightning
/cosmetics death soul_escape
/cosmetics trail rainbow
/cosmetics victory champion
/cosmetics clear all
```

### Permissions
```yaml
pvpkits.cosmetic.kill.lightning: true
pvpkits.cosmetic.death.soul: true
pvpkits.cosmetic.trail.rainbow: true
pvpkits.cosmetic.victory.champion: true
```

### Интеграция
```kotlin
// При убийстве
cosmeticsManager.playKillEffect(killer, victim)

// При смерти
cosmeticsManager.playDeathAnimation(victim)

// При победе
cosmeticsManager.playVictoryPose(winner)

// Trail обновляется каждый тик автоматически
```

---

## 4. 👥 Duel Challenge система

### Возможности
- ✅ Вызов конкретного игрока на дуэль
- ✅ Выбор кита для дуэли
- ✅ Система приглашений (60 сек таймаут)
- ✅ Автоматическая очистка просроченных вызовов
- ✅ Интеграция с улучшенной системой арен

### Команды
```bash
/challenge <игрок> <кит>   # Вызвать на дуэль
/duel accept <игрок>       # Принять вызов
/duel deny <игрок>         # Отклонить вызов
/duel cancel               # Отменить свой вызов
```

### Workflow
```
1. Игрок A: /challenge Steve crystal
   → Steve получает вызов на дуэль с китом Crystal

2. Игрок Steve: /duel accept A
   → Дуэль начинается автоматически
   → Игроки телепортируются на арену
   → Получают выбранный кит

3. После матча:
   → Арена автоматически сбрасывается
   → Блоки восстанавливаются
   → Обновляется рейтинг ELO
```

### Особенности
- Вызовы истекают через 60 секунд
- Нельзя вызвать игрока, который уже в матче
- Нельзя вызвать игрока, который в очереди
- Автоматическая очистка каждые 30 секунд

---

## 5. 🏟️ Улучшенная система арен

### Концепция
Новая система арен использует **шаблоны** (templates) и **инстансы** (instances) для правильной работы с картами.

#### Arena Template (Шаблон)
- Мастер-копия арены
- Содержит координаты спавнов
- Определяет границы арены
- Может ограничивать киты

#### Arena Instance (Инстанс)
- Активная копия для матча
- Отслеживает изменения блоков
- Автоматически сбрасывается после матча
- Восстанавливает все блоки

### Команды
```bash
# Управление шаблонами
/arena template create <name>     # Создать шаблон
/arena template list              # Список шаблонов
/arena template info <name>       # Информация о шаблоне
/arena template delete <name>     # Удалить шаблон

# Старые команды арен (совместимость)
/arena create <name>              # Создать арену (старая система)
/arena list                       # Список арен
/join [arena]                     # Присоединиться к арене
/leave                            # Покинуть арену
```

### Создание шаблона арены

#### Шаг 1: Подготовка
```bash
# Постройте арену в мире
# Определите две точки спавна
# Определите границы арены (min/max координаты)
```

#### Шаг 2: Создание шаблона
```bash
# Встаньте на первую точку спавна
/arena template create myarena

# Система сохранит вашу позицию как spawn1
# Вам нужно будет установить spawn2 и границы
```

#### Шаг 3: Настройка (в arena-templates.yml)
```yaml
templates:
  myarena:
    display-name: "My Arena"
    world: "world"
    enabled: true
    spawn1:
      x: 100.5
      y: 64.0
      z: 200.5
      yaw: 0.0
      pitch: 0.0
    spawn2:
      x: 120.5
      y: 64.0
      z: 220.5
      yaw: 180.0
      pitch: 0.0
    bounds:
      min:
        x: 90
        y: 60
        z: 190
      max:
        x: 130
        y: 80
        z: 230
    allowed-kits: []  # Пусто = все киты разрешены
```

### Как это работает

#### При старте дуэли:
1. Система ищет свободный инстанс или создает новый
2. Сохраняет все блоки в границах арены
3. Телепортирует игроков на спавны
4. Начинается матч

#### Во время матча:
- Игроки могут ломать/ставить блоки
- Все изменения отслеживаются
- Инстанс помечен как "занят"

#### После матча:
1. Инстанс освобождается
2. Все измененные блоки восстанавливаются
3. Арена готова к следующему матчу
4. После 10 матчей инстанс полностью сбрасывается

### Преимущества новой системы
✅ Блоки автоматически восстанавливаются
✅ Несколько матчей могут идти на одной арене (разные инстансы)
✅ Нет "мусора" после боев
✅ Оптимизированное использование памяти
✅ Поддержка kit-specific арен

### Технические детали

#### Отслеживание блоков
```kotlin
// Перед матчем
for (x in minX..maxX) {
    for (y in minY..maxY) {
        for (z in minZ..maxZ) {
            savedBlocks[location] = block.type
        }
    }
}

// После матча
savedBlocks.forEach { (location, material) ->
    if (block.type != material) {
        block.type = material  // Восстановить
    }
}
```

#### Управление инстансами
```kotlin
// Получить свободный инстанс
val instance = improvedArenaManager.getAvailableInstance(kitName)

// Начать матч
improvedArenaManager.startMatch(instance, player1, player2)

// Завершить матч
improvedArenaManager.endMatch(instance)
// → Автоматически восстанавливает блоки
```

### Интеграция с дуэлями
```kotlin
// DuelManager автоматически использует новую систему
fun startDirectDuel(player1: Player, player2: Player, kitName: String) {
    val instance = improvedArenaManager.getAvailableInstance(kitName)
    if (instance != null) {
        // Используем улучшенную систему
        improvedArenaManager.startMatch(instance, player1, player2)
    } else {
        // Fallback на старую систему
        // ...
    }
}
```

### Статистика
```bash
# В логах при запуске
║   Arena Templates: 3              ║
║   Active Instances: 2             ║
║   Players in Arenas: 4            ║
```

### Конфигурация
```yaml
# В arena-templates.yml
templates:
  pvp_arena_1:
    display-name: "PvP Arena 1"
    world: "arena1"
    enabled: true
    spawn1: { x: 0, y: 64, z: 10 }
    spawn2: { x: 0, y: 64, z: -10 }
    bounds:
      min: { x: -20, y: 60, z: -20 }
      max: { x: 20, y: 80, z: 20 }
    allowed-kits: []  # Все киты
  
  crystal_only:
    display-name: "Crystal Arena"
    world: "arena2"
    enabled: true
    spawn1: { x: 50, y: 64, z: 50 }
    spawn2: { x: 70, y: 64, z: 70 }
    bounds:
      min: { x: 40, y: 60, z: 40 }
      max: { x: 80, y: 80, z: 80 }
    allowed-kits: ["crystal"]  # Только Crystal кит
```

---

## 6. 👥 Party система (Legacy)

### Возможности
- ✅ Создание групп до 8 игроков
- ✅ Система приглашений (60 сек таймаут)
- ✅ Party chat
- ✅ Kick/Leave/Disband
- ✅ Автоматическая очистка при выходе

### Команды
```bash
/party create              # Создать группу
/party invite <игрок>      # Пригласить игрока
/party accept              # Принять приглашение
/party deny                # Отклонить приглашение
/party leave               # Покинуть группу
/party kick <игрок>        # Выгнать игрока (только лидер)
/party disband             # Распустить группу (только лидер)
/party list                # Список участников
/party chat <сообщение>    # Чат группы
/p c <сообщение>           # Алиас для party chat
```

### Workflow
```
1. Игрок A: /party create
   → Группа создана, A - лидер

2. Игрок A: /party invite B
   → B получает приглашение (60 сек)

3. Игрок B: /party accept
   → B присоединился к группе

4. Игрок A: /party chat Hello!
   → Сообщение видят все участники группы

5. Игрок B: /party leave
   → B покинул группу
```

### Будущие возможности
- Team дуэли (2v2, 3v3)
- Party tournaments
- Shared stats
- Party quests

---

## 7. 📊 Улучшенная статистика

### Новые таблицы

#### Kit-specific stats
```sql
CREATE TABLE kit_stats (
    uuid TEXT,
    kit_name TEXT,
    kills INTEGER,
    deaths INTEGER,
    wins INTEGER,
    losses INTEGER,
    damage_dealt REAL,
    damage_taken REAL,
    PRIMARY KEY (uuid, kit_name)
)
```

#### Match history
```sql
CREATE TABLE match_history (
    id INTEGER PRIMARY KEY,
    player_uuid TEXT,
    opponent_uuid TEXT,
    kit_name TEXT,
    result TEXT,
    duration INTEGER,
    kills INTEGER,
    deaths INTEGER,
    damage_dealt REAL,
    damage_taken REAL,
    timestamp INTEGER
)
```

#### Daily stats
```sql
CREATE TABLE daily_stats (
    uuid TEXT,
    date TEXT,
    kills INTEGER,
    deaths INTEGER,
    wins INTEGER,
    losses INTEGER,
    playtime INTEGER,
    PRIMARY KEY (uuid, date)
)
```

#### Achievements
```sql
CREATE TABLE achievements (
    uuid TEXT,
    achievement_id TEXT,
    unlocked_at INTEGER,
    PRIMARY KEY (uuid, achievement_id)
)
```

### Достижения
- 🩸 **First Blood** - первое убийство
- 🔥 **Killing Spree** - 5 киллстрик
- ⚡ **Unstoppable** - 10 киллстрик
- 👑 **Godlike** - 20 киллстрик
- 💯 **Centurion** - 100 убийств
- ⚔ **Gladiator** - 500 убийств
- 🏆 **Legend** - 1000 убийств
- 🎖 **Tournament Winner** - победа в турнире
- ✨ **Perfect Match** - победа без смертей
- 🎯 **Kit Master** - 50 убийств одним китом

### API
```kotlin
// Запись статистики по киту
enhancedStatsManager.recordKitStats(
    uuid, kitName, kills, deaths, wins, losses, damageDealt, damageTaken
)

// Запись матча в историю
enhancedStatsManager.recordMatch(
    playerUUID, opponentUUID, kitName, won, duration, kills, deaths, damageDealt, damageTaken
)

// Получить статистику по киту
val kitStats = enhancedStatsManager.getKitStats(uuid, kitName)

// Получить последние матчи
val matches = enhancedStatsManager.getRecentMatches(uuid, limit = 10)

// Разблокировать достижение
enhancedStatsManager.unlockAchievement(uuid, "first_blood")
```

---

## 🔧 Интеграция систем

### Duel System + Rating
```kotlin
// После окончания дуэли
ratingManager.recordMatch(winnerUUID, loserUUID)
// Автоматически обновляет рейтинги обоих игроков
```

### Duel System + Replay
```kotlin
// При старте дуэли
val recorder = replayManager.startRecording(matchId, p1, p2, kit)

// При окончании
replayManager.stopRecording(matchId, winnerUUID)
```

### Duel System + Cosmetics
```kotlin
// При убийстве
cosmeticsManager.playKillEffect(killer, victim)
cosmeticsManager.playDeathAnimation(victim)

// При победе
cosmeticsManager.playVictoryPose(winner)
```

### Tournament + Rating
```kotlin
// Турнирные матчи учитываются в рейтинге
// Победы дают больше ELO
```

### Stats + Achievements
```kotlin
// Автоматическая проверка достижений
if (stats.currentKillstreak == 5) {
    enhancedStatsManager.unlockAchievement(uuid, "killing_spree")
}
```

---

## 📈 Performance

### Оптимизации
- ✅ Caffeine кэширование для рейтингов
- ✅ Async сохранение реплеев
- ✅ Batch операции для статистики
- ✅ Lazy loading реплеев
- ✅ Автоочистка старых данных

### Memory Usage
```
Rating Manager: ~100 KB per 1000 players
Replay Manager: ~5 MB per 100 replays
Cosmetics Manager: ~10 KB per 1000 players
Party Manager: ~5 KB per 100 parties
Enhanced Stats: ~50 KB per 1000 players
```

### Database Size
```
elo_ratings: ~1 KB per player
kit_stats: ~500 bytes per kit per player
match_history: ~200 bytes per match
achievements: ~100 bytes per achievement
replays: ~1-5 MB per replay (зависит от длительности)
```

---

## 🎮 Конфигурация

Добавьте в `config.yml`:

```yaml
# Rating system
rating:
  enabled: true
  starting-rating: 1000
  k-factor: 32
  k-factor-new: 40  # For players with <30 matches
  min-rating: 0
  max-rating: 3000

# Replay system
replay:
  enabled: true
  max-replays-per-player: 10
  record-interval-ticks: 1  # Record every tick
  auto-cleanup: true

# Cosmetics
cosmetics:
  enabled: true
  trail-update-interval: 1  # ticks
  allow-in-combat: true

# Party system
party:
  enabled: true
  max-size: 8
  invite-timeout: 60  # seconds
  allow-pvp: false  # Can party members fight each other?

# Enhanced stats
enhanced-stats:
  enabled: true
  track-kit-stats: true
  track-match-history: true
  track-daily-stats: true
  achievements-enabled: true
  max-match-history: 100  # per player
```

---

## 🚀 Использование

### Для игроков

```bash
# Проверить свой рейтинг
/rating

# Посмотреть топ игроков
/leaderboard

# Настроить косметику
/cosmetics

# Создать группу
/party create
/party invite Steve

# Чат группы
/p c Hello party!
```

### Для администраторов

```bash
# Создать турнир с рейтингом
/tournament create "Ranked Cup" crystal 32 single

# Посмотреть статистику игрока
/stats Player

# Дать косметику
/lp user Player permission set pvpkits.cosmetic.kill.lightning true
```

---

## 📝 TODO / Будущие улучшения

### Replay система
- [ ] GUI для просмотра реплеев
- [ ] Экспорт в видео
- [ ] Sharing реплеев между игроками
- [ ] Highlight reel (лучшие моменты)

### Rating система
- [ ] Сезонные рейтинги
- [ ] Decay (снижение рейтинга за неактивность)
- [ ] Placement matches (калибровка)
- [ ] MMR matchmaking в дуэлях

### Cosmetics
- [ ] GUI для выбора
- [ ] Анимированные preview
- [ ] Кастомные частицы
- [ ] Sound effects

### Party система
- [ ] Team дуэли (2v2, 3v3)
- [ ] Party tournaments
- [ ] Party quests
- [ ] Voice chat integration

### Enhanced Stats
- [ ] Heatmaps
- [ ] Графики прогресса
- [ ] Сравнение с другими игроками
- [ ] Weekly/Monthly reports

---

**Made with ❤️ for PvPKits v2.2 - 2026 Edition**


---

## 🏟️ Турниры (Tournament System)

### Команды
```bash
/tournament create <название> <кит> <макс_игроков> [single|double]
/tournament join <id>
/tournament leave
/tournament start <id>
/tournament list
/tournament info <id>
```

### Пример использования
```bash
# Создать турнир
/tournament create "Friday Cup" crystal 16 single

# Игроки присоединяются
/tournament join abc123

# Автостарт при заполнении или ручной старт
/tournament start abc123
```

### Bracket Types
- **Single Elimination** - проигравший выбывает (быстро)
- **Double Elimination** - две жизни (справедливо)

---

## 👁️ Spectator Mode

### Команды
```bash
/spectate <игрок>    # Начать наблюдение
/spec <игрок>        # Алиас
/stopspectating      # Остановить
/stopspec            # Алиас
```

### Возможности
- Режим наблюдателя (GameMode.SPECTATOR)
- Автоматический Night Vision
- Невидимость для игроков
- Автопереключение на убийцу при смерти цели
- Счетчик зрителей

---

## 📦 Сборка и установка

```bash
# Windows
mvnw.cmd clean package

# Linux/Mac
./mvnw clean package
```

Готовый плагин: `target/PvPKits-1.0.0.jar`

---

## ⚙️ Конфигурация (config.yml)

```yaml
# Lobby
lobby:
  teleport-on-join: true
  spawn:
    world: lobby
    x: 0
    y: 64
    z: 0

# Rating system
rating:
  enabled: true
  starting-rating: 1000
  k-factor: 32

# Replay system
replay:
  enabled: true
  max-replays-per-player: 10

# Cosmetics
cosmetics:
  enabled: true
  trail-update-interval: 1

# Party system
party:
  enabled: true
  max-size: 8
  invite-timeout: 60

# Enhanced stats
enhanced-stats:
  enabled: true
  track-kit-stats: true
  achievements-enabled: true
```

---

## 🎯 Permissions

```yaml
# Основные
pvpkits.use: true
pvpkits.admin: op

# Турниры
pvpkits.tournament.create: op
pvpkits.tournament.start: op

# Рейтинг
pvpkits.rating.others: op

# Spectator
pvpkits.spectate: true

# Cosmetics (примеры)
pvpkits.cosmetic.kill.lightning: true
pvpkits.cosmetic.death.soul: true
pvpkits.cosmetic.trail.rainbow: true
pvpkits.cosmetic.victory.champion: true
```

---

## 🚀 Быстрый старт

### Для игроков
```bash
/kit                    # Выбрать кит
/duel queue crystal     # Дуэль
/rating                 # Мой рейтинг
/cosmetics              # Настроить косметику
/party create           # Создать группу
/spectate Player        # Наблюдать за игроком
```

### Для администраторов
```bash
/tournament create "Weekend Cup" sword 32 single
/arena create arena1
/stats Player
```

---

## 📊 Технологии

- **Kotlin 2.3.0** + **Java 21**
- **Paper API 1.21.8**
- **Kotlin Coroutines** - асинхронность
- **HikariCP** - connection pooling
- **Caffeine** - кэширование
- **SQLite** - база данных
- **Adventure API** - MiniMessage

---

## 🔧 Troubleshooting

### Проблема: Турнир не стартует
- Проверьте количество игроков (минимум 2)
- `/tournament info <id>` для проверки статуса
- `/tournament start <id>` для ручного старта

### Проблема: Низкая производительность
- Проверьте Java 21+
- Выделите больше RAM серверу
- Проверьте логи на ошибки

### Проблема: Рейтинг не обновляется
- Убедитесь что `rating.enabled: true` в config.yml
- Проверьте права доступа к базе данных
- Перезапустите сервер

---

**Made with ❤️ for PvPKits v2.2 - 2026 Edition**


---

## 🛤 Новый кит: Cart PvP

### Описание
Cart PvP - это уникальный стиль боя, основанный на мобильности и тактическом использовании вагонеток и рельсов. Идеально подходит для игроков, которые любят динамичный и непредсказуемый бой.

### Особенности кита
- **Мобильность**: 16 вагонеток для быстрого перемещения
- **Рельсовая сеть**: 64 powered rails + 64 обычных rails
- **TNT вагонетки**: 8 взрывных вагонеток для атаки
- **Тактика**: Cobweb для замедления врагов
- **Дальний бой**: Crossbow с Quick Charge III

### Стратегия использования

#### Offensive (Атака)
1. Быстро размещайте рельсы к противнику
2. Используйте TNT minecart для взрывного урона
3. Crossbow для атаки на расстоянии
4. Cobweb для контроля зоны

#### Defensive (Защита)
1. Создавайте рельсовые пути для отступления
2. Используйте вагонетки для быстрого ухода
3. Activator rails для автоматических ловушек
4. Ender pearls для экстренной телепортации

#### Mobility (Мобильность)
- Powered rails + Redstone torch = максимальная скорость
- Depth Strider III на ботинках для движения в воде
- 16 вагонеток = много попыток маневра

### Комбо-атаки
```
1. Rail Rush:
   - Разместить powered rails
   - Сесть в minecart
   - Разогнаться и выпрыгнуть с мечом

2. TNT Trap:
   - Разместить rails к врагу
   - Запустить TNT minecart
   - Взорвать с помощью activator rail

3. Web Cage:
   - Окружить врага cobweb
   - Атаковать crossbow издалека
   - Добить мечом с knockback

4. Escape Route:
   - Разместить rails для отступления
   - Использовать minecart для ухода
   - Ender pearl если нужно
```

### Преимущества
✅ Высокая мобильность
✅ Непредсказуемость
✅ Тактическое разнообразие
✅ Контроль территории
✅ Взрывной урон (TNT minecarts)

### Недостатки
❌ Требует времени на установку рельсов
❌ Средняя сложность
❌ Нужно пространство для маневра
❌ Уязвим при установке рельсов

### Идеальные условия
- Открытые арены
- Плоская местность
- Средние и длинные дистанции
- 1v1 дуэли

### Counters (Контр-киты)
- **Archer** - может атаковать на расстоянии
- **Crystal** - взрывы разрушают рельсы
- **Mace** - воздушные атаки игнорируют рельсы

### Tips & Tricks
💡 Всегда держите несколько вагонеток в hotbar
💡 Используйте Shift для точной установки рельсов
💡 TNT minecart взрывается при падении с высоты
💡 Activator rails могут выбрасывать игроков из вагонеток
💡 Cobweb замедляет и вагонетки тоже

---
