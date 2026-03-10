package com.pvpkits.cosmetics

import com.pvpkits.PvPKitsPlugin
import com.pvpkits.utils.TextUtils
import net.kyori.adventure.title.Title
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CosmeticsManager(private val plugin: PvPKitsPlugin) {

    private val playerCosmetics = ConcurrentHashMap<UUID, PlayerCosmetics>()

    fun getCosmetics(uuid: UUID): PlayerCosmetics {
        return playerCosmetics.getOrPut(uuid) { PlayerCosmetics(uuid) }
    }

    fun setKillEffect(uuid: UUID, effect: KillEffect?) {
        getCosmetics(uuid).killEffect = effect
    }

    fun setDeathAnimation(uuid: UUID, animation: DeathAnimation?) {
        getCosmetics(uuid).deathAnimation = animation
    }

    fun setTrailEffect(uuid: UUID, trail: TrailEffect?) {
        getCosmetics(uuid).trailEffect = trail
    }

    fun setVictoryPose(uuid: UUID, pose: VictoryPose?) {
        getCosmetics(uuid).victoryPose = pose
    }

    fun playKillEffect(killer: Player, victim: Player) {
        getCosmetics(killer.uniqueId).killEffect?.play(killer, victim.location)
    }

    fun playDeathAnimation(victim: Player) {
        getCosmetics(victim.uniqueId).deathAnimation?.play(victim.location)
    }

    fun playVictoryPose(winner: Player) {
        getCosmetics(winner.uniqueId).victoryPose?.play(winner)
    }

    fun updateTrails() {
        plugin.server.onlinePlayers.forEach { player ->
            getCosmetics(player.uniqueId).trailEffect?.update(player)
        }
    }

    fun cleanupPlayer(uuid: UUID) {
        playerCosmetics.remove(uuid)
    }

    fun getMemoryStats(): Map<String, Any> {
        return mapOf("players_with_cosmetics" to playerCosmetics.size)
    }
}

data class PlayerCosmetics(
    val uuid: UUID,
    var killEffect: KillEffect? = null,
    var deathAnimation: DeathAnimation? = null,
    var trailEffect: TrailEffect? = null,
    var victoryPose: VictoryPose? = null
)

enum class KillEffect(val displayName: String, val permission: String) {
    LIGHTNING("Lightning Strike", "pvpkits.cosmetic.kill.lightning") {
        override fun play(killer: Player, location: Location) {
            location.world?.strikeLightningEffect(location)
            killer.playSound(killer.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.0f)
        }
    },
    EXPLOSION("Explosion", "pvpkits.cosmetic.kill.explosion") {
        override fun play(killer: Player, location: Location) {
            location.world?.spawnParticle(Particle.EXPLOSION, location, 3, 0.5, 0.5, 0.5, 0.0)
            location.world?.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f)
        }
    },
    BLOOD("Blood Splash", "pvpkits.cosmetic.kill.blood") {
        override fun play(killer: Player, location: Location) {
            location.world?.spawnParticle(
                Particle.DUST,
                location.add(0.0, 1.0, 0.0),
                50,
                0.5,
                0.5,
                0.5,
                1.0,
                Particle.DustOptions(Color.RED, 2.0f)
            )
        }
    },
    FIREWORK("Firework", "pvpkits.cosmetic.kill.firework") {
        override fun play(killer: Player, location: Location) {
            location.world?.spawnParticle(Particle.FIREWORK, location.add(0.0, 1.0, 0.0), 100, 0.5, 0.5, 0.5, 0.1)
            location.world?.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f)
        }
    },
    HEARTS("Hearts", "pvpkits.cosmetic.kill.hearts") {
        override fun play(killer: Player, location: Location) {
            location.world?.spawnParticle(Particle.HEART, location.add(0.0, 2.0, 0.0), 20, 0.5, 0.5, 0.5, 0.0)
        }
    };

    abstract fun play(killer: Player, location: Location)
}

enum class DeathAnimation(val displayName: String, val permission: String) {
    SOUL_ESCAPE("Soul Escape", "pvpkits.cosmetic.death.soul") {
        override fun play(location: Location) {
            location.world?.spawnParticle(Particle.SOUL, location.add(0.0, 1.0, 0.0), 30, 0.3, 0.3, 0.3, 0.05)
            location.world?.playSound(location, Sound.ENTITY_VEX_DEATH, 1.0f, 0.8f)
        }
    },
    SMOKE_POOF("Smoke Poof", "pvpkits.cosmetic.death.smoke") {
        override fun play(location: Location) {
            location.world?.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, location.add(0.0, 0.5, 0.0), 50, 0.5, 0.5, 0.5, 0.05)
        }
    },
    FLAME_BURST("Flame Burst", "pvpkits.cosmetic.death.flame") {
        override fun play(location: Location) {
            location.world?.spawnParticle(Particle.FLAME, location.add(0.0, 1.0, 0.0), 40, 0.5, 0.5, 0.5, 0.1)
            location.world?.playSound(location, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f)
        }
    },
    ENDER_TELEPORT("Ender Teleport", "pvpkits.cosmetic.death.ender") {
        override fun play(location: Location) {
            location.world?.spawnParticle(Particle.PORTAL, location.add(0.0, 1.0, 0.0), 100, 0.5, 1.0, 0.5, 1.0)
            location.world?.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
        }
    };

    abstract fun play(location: Location)
}

enum class TrailEffect(val displayName: String, val permission: String) {
    RAINBOW("Rainbow", "pvpkits.cosmetic.trail.rainbow") {
        private var hue = 0f
        override fun update(player: Player) {
            val color = Color.fromRGB(java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f))
            player.world.spawnParticle(
                Particle.DUST,
                player.location.add(0.0, 0.1, 0.0),
                3,
                0.2,
                0.1,
                0.2,
                0.0,
                Particle.DustOptions(color, 1.0f)
            )
            hue += 0.01f
            if (hue > 1.0f) hue = 0f
        }
    },
    FIRE("Fire", "pvpkits.cosmetic.trail.fire") {
        override fun update(player: Player) {
            player.world.spawnParticle(Particle.FLAME, player.location, 2, 0.1, 0.0, 0.1, 0.0)
        }
    },
    SPARKLE("Sparkle", "pvpkits.cosmetic.trail.sparkle") {
        override fun update(player: Player) {
            player.world.spawnParticle(Particle.END_ROD, player.location.add(0.0, 0.5, 0.0), 1, 0.2, 0.2, 0.2, 0.0)
        }
    },
    CLOUD("Cloud", "pvpkits.cosmetic.trail.cloud") {
        override fun update(player: Player) {
            player.world.spawnParticle(Particle.CLOUD, player.location, 3, 0.2, 0.1, 0.2, 0.0)
        }
    };

    abstract fun update(player: Player)
}

enum class VictoryPose(val displayName: String, val permission: String) {
    CHAMPION("Champion", "pvpkits.cosmetic.victory.champion") {
        override fun play(player: Player) {
            player.world.spawnParticle(Particle.TOTEM_OF_UNDYING, player.location.add(0.0, 1.0, 0.0), 50, 0.5, 1.0, 0.5, 0.1)
            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
            player.showTitle(
                Title.title(
                    TextUtils.parseAuto("<gold><bold>Victory"),
                    TextUtils.parseAuto("<yellow>You are the champion!"),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000))
                )
            )
        }
    },
    FIREWORKS("Fireworks", "pvpkits.cosmetic.victory.fireworks") {
        override fun play(player: Player) {
            repeat(5) { i ->
                player.server.scheduler.runTaskLater(player.server.pluginManager.getPlugin("PvPKits")!!, Runnable {
                    player.world.spawnParticle(Particle.FIREWORK, player.location.add(0.0, 2.0, 0.0), 30, 0.5, 0.5, 0.5, 0.2)
                    player.world.playSound(player.location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f + (i * 0.1f))
                }, i * 10L)
            }
        }
    },
    LIGHTNING("Lightning", "pvpkits.cosmetic.victory.lightning") {
        override fun play(player: Player) {
            player.world.strikeLightningEffect(player.location)
            player.playSound(player.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f)
        }
    };

    abstract fun play(player: Player)
}
