package com.ququla89.digitalhorror.feature

import com.ququla89.digitalhorror.config.AudioAnomalySettings
import com.ququla89.digitalhorror.util.MathUtil
import com.ququla89.digitalhorror.util.isNear
import com.ququla89.digitalhorror.util.rollChance
import com.ququla89.digitalhorror.util.runTrackedTaskLater
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.random.Random

class AudioAnomalyFeature(private val plugin: JavaPlugin, private val settings: AudioAnomalySettings) :
    HorrorFeature,
    Listener {
    override var isEnabled: Boolean = false
        private set

    private val taskIds = mutableSetOf<Int>()
    private val motionStates = mutableMapOf<UUID, MotionState>()
    private val activePhantomMiners = mutableMapOf<UUID, PhantomMinerState>()

    override fun enable() {
        if (isEnabled || !settings.enabled) return
        isEnabled = true
        plugin.server.pluginManager.registerEvents(this, plugin)

        taskIds +=
            Bukkit.getScheduler()
                .runTaskTimer(
                    plugin,
                    Runnable(::checkMovement),
                    settings.movementCheckIntervalTicks,
                    settings.movementCheckIntervalTicks,
                ).taskId
        if (settings.phantomMiner.enabled) {
            taskIds +=
                Bukkit.getScheduler()
                    .runTaskTimer(
                        plugin,
                        Runnable(::checkPhantomMiners),
                        settings.phantomMiner.checkIntervalTicks,
                        settings.phantomMiner.checkIntervalTicks,
                    ).taskId
            taskIds +=
                Bukkit.getScheduler()
                    .runTaskTimer(plugin, Runnable(::updatePhantomMiners), 1L, 1L)
                    .taskId
        }
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
        HandlerList.unregisterAll(this)
        taskIds.forEach(Bukkit.getScheduler()::cancelTask)
        taskIds.clear()
        motionStates.clear()
        activePhantomMiners.clear()
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val effect = settings.glassBreak
        if (
            !isEnabled ||
            !effect.enabled ||
            event.block.type.isGlassBlock() ||
            !effect.chance.rollChance()
        ) {
            return
        }

        event.player.playSound(
            event.player.location,
            Sound.BLOCK_GLASS_BREAK,
            SoundCategory.BLOCKS,
            effect.volume,
            effect.pitch,
        )
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
        plugin.runTrackedTaskLater(taskIds, settings.delayedFootstepTicks) {
            if (!isEnabled || !player.isOnline ||
                !player.location.isNear(stopLocation, 0.2)
            ) {
                return@runTrackedTaskLater
            }
            val soundLocation = MathUtil.behindLocation(player, 2.2)
            player.playSound(
                soundLocation,
                Sound.BLOCK_STONE_STEP,
                SoundCategory.AMBIENT,
                settings.volume,
                randomPitch(),
            )
        }
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

    private fun checkPhantomMiners() {
        if (!isEnabled) return
        val effect = settings.phantomMiner
        Bukkit.getOnlinePlayers().forEach { player ->
            if (
                player.isDead ||
                player.uniqueId in activePhantomMiners ||
                !effect.chance.rollChance()
            ) {
                return@forEach
            }

            val source = findPhantomMinerSource(player) ?: return@forEach
            activePhantomMiners[player.uniqueId] =
                PhantomMinerState(source = source, blocksRemaining = effect.blockCount)
        }
    }

    private fun updatePhantomMiners() {
        if (!isEnabled) return
        val effect = settings.phantomMiner
        val iterator = activePhantomMiners.iterator()
        while (iterator.hasNext()) {
            val (playerId, miner) = iterator.next()
            val player = Bukkit.getPlayer(playerId)
            if (
                player == null ||
                !player.isOnline ||
                player.world !== miner.source.world ||
                player.location.isNear(miner.source, effect.approachDistance)
            ) {
                iterator.remove()
                continue
            }

            if (miner.ticksUntilNextSound > 0L) {
                miner.ticksUntilNextSound--
                if (miner.ticksUntilNextSound > 0L) continue
            }

            if (miner.hitsPlayed < effect.hitsPerBlock) {
                player.playSound(
                    miner.source,
                    Sound.BLOCK_STONE_HIT,
                    SoundCategory.BLOCKS,
                    effect.volume,
                    effect.pitch,
                )
                miner.hitsPlayed++
                miner.ticksUntilNextSound = effect.hitIntervalTicks
                continue
            }

            player.playSound(
                miner.source,
                Sound.BLOCK_STONE_BREAK,
                SoundCategory.BLOCKS,
                effect.volume,
                effect.pitch,
            )
            miner.blocksRemaining--
            if (miner.blocksRemaining <= 0) {
                iterator.remove()
            } else {
                miner.source = findAdjacentSolidSource(miner.source)
                miner.hitsPlayed = 0
                miner.ticksUntilNextSound = effect.breakIntervalTicks
            }
        }
    }

    private fun findPhantomMinerSource(player: Player): Location? {
        val effect = settings.phantomMiner
        val minVerticalOffset = minOf(effect.minVerticalOffset, effect.maxVerticalOffset)
        val maxVerticalOffset = maxOf(effect.minVerticalOffset, effect.maxVerticalOffset)

        repeat(SOURCE_SEARCH_ATTEMPTS) {
            val verticalOffset =
                if (maxVerticalOffset - minVerticalOffset < 0.01) {
                    minVerticalOffset
                } else {
                    Random.nextDouble(minVerticalOffset, maxVerticalOffset)
                }
            val candidate =
                player.eyeLocation.clone()
                    .add(MathUtil.randomHorizontalOffset(effect.minDistance, effect.maxDistance))
                    .add(0.0, verticalOffset, 0.0)
            if (candidate.world.isChunkLoaded(candidate.blockX shr 4, candidate.blockZ shr 4) &&
                candidate.block.type.isSolid
            ) {
                return candidate.block.location.add(0.5, 0.5, 0.5)
            }
        }
        return null
    }

    private fun findAdjacentSolidSource(current: Location): Location {
        val adjacent =
            MINER_BLOCK_FACES.shuffled()
                .asSequence()
                .map { face -> current.block.getRelative(face) }
                .firstOrNull { block -> block.type.isSolid }
        return adjacent?.location?.add(0.5, 0.5, 0.5) ?: current
    }

    private fun Material.isGlassBlock(): Boolean = this == Material.GLASS ||
        this == Material.GLASS_PANE ||
        this == Material.TINTED_GLASS ||
        name.endsWith("_STAINED_GLASS") ||
        name.endsWith("_GLASS_PANE")

    private fun randomPitch(): Float = Random.nextDouble(
        settings.pitchMin.toDouble(),
        settings.pitchMax.toDouble().coerceAtLeast(settings.pitchMin + 0.01),
    ).toFloat()

    private data class MotionState(val location: Location, val wasMoving: Boolean)

    private data class PhantomMinerState(
        var source: Location,
        var blocksRemaining: Int,
        var hitsPlayed: Int = 0,
        var ticksUntilNextSound: Long = 0L,
    )

    private companion object {
        const val SOURCE_SEARCH_ATTEMPTS = 24
        val MINER_BLOCK_FACES =
            listOf(
                BlockFace.NORTH,
                BlockFace.SOUTH,
                BlockFace.EAST,
                BlockFace.WEST,
                BlockFace.UP,
                BlockFace.DOWN,
            )
    }
}
