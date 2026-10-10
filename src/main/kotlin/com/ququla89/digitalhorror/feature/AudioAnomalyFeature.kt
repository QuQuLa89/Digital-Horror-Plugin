package com.ququla89.digitalhorror.feature

import com.ququla89.digitalhorror.config.AudioAnomalySettings
import com.ququla89.digitalhorror.util.MathUtil
import com.ququla89.digitalhorror.util.isNear
import com.ququla89.digitalhorror.util.rollChance
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.random.Random

class AudioAnomalyFeature(private val plugin: JavaPlugin, private val settings: AudioAnomalySettings) : HorrorFeature {
    override var isEnabled: Boolean = false
        private set

    private val taskIds = mutableSetOf<Int>()
    private val motionStates = mutableMapOf<UUID, MotionState>()

    override fun enable() {
        if (isEnabled || !settings.enabled) return
        isEnabled = true

        taskIds +=
            Bukkit.getScheduler()
                .runTaskTimer(
                    plugin,
                    Runnable(::checkMovement),
                    settings.movementCheckIntervalTicks,
                    settings.movementCheckIntervalTicks,
                ).taskId
        taskIds +=
            Bukkit.getScheduler()
                .runTaskTimer(
                    plugin,
                    Runnable(::checkAmbientAnomalies),
                    settings.ambientCheckIntervalTicks,
                    settings.ambientCheckIntervalTicks,
                ).taskId
    }

    override fun disable() {
        if (!isEnabled) return
        isEnabled = false
        taskIds.forEach(Bukkit.getScheduler()::cancelTask)
        taskIds.clear()
        motionStates.clear()
    }

    private fun checkMovement() {
        if (!isEnabled) return
        val onlineIds = Bukkit.getOnlinePlayers().mapTo(mutableSetOf()) { it.uniqueId }
        motionStates.keys.removeIf { it !in onlineIds }

        Bukkit.getOnlinePlayers().forEach { player ->
            val current = player.location.clone()
            val previous = motionStates[player.uniqueId]
            val moving =
                previous != null &&
                    current.world === previous.location.world &&
                    current.distanceSquared(previous.location) > 0.0025

            if (previous?.wasMoving == true && !moving && settings.footstepChance.rollChance()) {
                scheduleDelayedFootstep(player, current)
            }
            motionStates[player.uniqueId] = MotionState(current, moving)
        }
    }

    private fun scheduleDelayedFootstep(player: Player, stopLocation: Location) {
        val task =
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable {
                    if (!isEnabled || !player.isOnline || !player.location.isNear(stopLocation, 0.2)) return@Runnable
                    val soundLocation = MathUtil.behindLocation(player, 2.2)
                    player.playSound(
                        soundLocation,
                        Sound.BLOCK_STONE_STEP,
                        SoundCategory.AMBIENT,
                        settings.volume,
                        randomPitch(),
                    )
                },
                settings.delayedFootstepTicks,
            )
        taskIds += task.taskId
    }

    private fun checkAmbientAnomalies() {
        if (!isEnabled) return
        Bukkit.getOnlinePlayers().forEach { player ->
            if (settings.distantSoundChance.rollChance()) playDistantSound(player)
            if (settings.silenceChance.rollChance()) createSuddenSilence(player)
        }
    }

    private fun playDistantSound(player: Player) {
        val location =
            player.location.clone().add(
                MathUtil.randomHorizontalOffset(settings.minDistance, settings.maxDistance),
            )
        val sound = if (Random.nextBoolean()) Sound.BLOCK_ANVIL_LAND else Sound.BLOCK_CHEST_OPEN
        player.playSound(location, sound, SoundCategory.AMBIENT, settings.volume, randomPitch())
    }

    private fun createSuddenSilence(player: Player) {
        player.stopSound(SoundCategory.AMBIENT)
        player.stopSound(SoundCategory.BLOCKS)
    }

    private fun randomPitch(): Float = Random.nextDouble(
        settings.pitchMin.toDouble(),
        settings.pitchMax.toDouble().coerceAtLeast(settings.pitchMin + 0.01),
    ).toFloat()

    private data class MotionState(val location: Location, val wasMoving: Boolean)
}
