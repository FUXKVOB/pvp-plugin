# PvPKits Plugin v0.3.0

Advanced PvP kits plugin for Minecraft `1.21.8` on Paper, built with Kotlin `2.3` and Java `21`.

## Build

```bash
# Windows
mvnw.cmd clean package

# Linux / macOS
./mvnw clean package
```

Release artifact: `target/PvPKits-0.3.0.jar`

## Highlights

- Kit GUI with cached item rendering and PDC-backed actions
- Duel, queue, party, arena, rating, replay, tournament, and cosmetics systems
- SQLite + HikariCP persistence
- Adventure and MiniMessage powered messages
- Java 21 / Paper 1.21.8 friendly runtime

## Install

1. Build the plugin.
2. Copy `target/PvPKits-0.3.0.jar` into your server `plugins/` folder.
3. Restart the Paper server.

## Notes

- Primary plugin metadata lives in `src/main/resources/plugin.yml`.
- Build verified with Java 21.
- Current release line: `v0.3.0`.
