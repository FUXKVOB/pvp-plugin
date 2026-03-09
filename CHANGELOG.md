# Changelog

## v0.2.0 - 2026-03-09

- Upgraded the plugin release version to `0.2.0`.
- Fixed the `duel` command registration conflict and kept queue access available through `duelqueue`.
- Moved kit GUI and kit compass identification to safer `InventoryHolder` and PDC-based handling.
- Unified stats, rating, and enhanced stats access around the shared database manager.
- Removed unsafe async world access from arena block snapshot/reset flow and replaced it with scheduled batched processing.
- Modernized several player-facing messages and GUI elements to use Adventure/Paper-friendly component APIs.
- Removed the outdated `paper-plugin.yml` descriptor to avoid metadata drift with `plugin.yml`.
- Fixed the Maven wrapper properties so the project can build reliably again.
- Disabled generation of `dependency-reduced-pom.xml` during release builds.

## Notes

- Release artifact: `target/PvPKits-0.2.0.jar`
- Build verified with Java 21 and Maven wrapper.
