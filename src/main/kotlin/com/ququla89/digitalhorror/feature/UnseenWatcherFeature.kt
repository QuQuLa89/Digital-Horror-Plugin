package com.ququla89.digitalhorror.feature

import com.ququla89.digitalhorror.config.UnseenWatcherSettings
import com.ququla89.digitalhorror.util.MathUtil
import com.ququla89.digitalhorror.util.rollChance
import com.ququla89.digitalhorror.util.runTrackedTaskLater
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.random.Random

class UnseenWatcherFeature(private val plugin: JavaPlugin, private val settings: UnseenWatcherSettings) :
    HorrorFeature {
    override var isEnabled: Boolean = false
        private set

    private val taskIds = mutableSetOf<Int>()
    private val watchers = mutableMapOf<UUID, ActiveWatcher>()
    private val temporaryTorches = mutableSetOf<TemporaryTorch>()

    override fun enable() {
        if (isEnabled || !settings.enabled) return
        isEnabled = true

        taskIds +=
            Bukkit.getScheduler()
                .runTaskTimer(
                    plugin,
                    Runnable(::runSpawnCheck),
                    settings.checkIntervalTicks,
                    settings.checkIntervalTicks,
                ).taskId
        taskIds +=
            Bukkit.getScheduler()
                .runTaskTimer(plugin, Runnable(::observeWatchers), 2L, 2L)
                .taskId
    }

    override fun disable() {
        if (!isEnabled) return
        isEnabled = false
        taskIds.forEach(Bukkit.getScheduler()::cancelTask)
        taskIds.clear()
        watchers.values.forEach { it.entity.remove() }
        watchers.clear()
        temporaryTorches.toList().forEach(::restoreTorch)
    }

    private fun runSpawnCheck() {
        if (!isEnabled) return
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.isDead) return@forEach
            if (!watchers.containsKey(player.uniqueId) && settings.spawnChance.rollChance()) {
                spawnWatcher(player)
            }
            if (settings.environmentChange.enabled && settings.environmentChange.chance.rollChance()) {
                placeTemporaryTorch(player)
            }
        }
    }

    private fun spawnWatcher(player: Player) {
        val distance = Random.nextDouble(
            settings.minDistance,
            settings.maxDistance.coerceAtLeast(settings.minDistance + 0.01),
        )
        val spawnLocation = MathUtil.behindLocation(player, distance, settings.verticalOffset)
        if (!spawnLocation.world.isChunkLoaded(spawnLocation.blockX shr 4, spawnLocation.blockZ shr 4) ||
            !spawnLocation.block.isPassable
        ) {
            return
        }

        val head =
            ItemStack(Material.PLAYER_HEAD).apply {
                itemMeta =
                    (itemMeta as SkullMeta).apply {
                        owningPlayer = player
                    }
            }

        val watcher =
            spawnLocation.world.spawn(spawnLocation, ArmorStand::class.java) { stand ->
                stand.isVisible = false
                stand.isSmall = true
                stand.isMarker = true
                stand.setGravity(false)
                stand.isInvulnerable = true
                stand.isSilent = true
                stand.isCollidable = false
                stand.isPersistent = false
                stand.equipment.helmet = head
            }

        watchers[player.uniqueId]?.entity?.remove()
        watchers[player.uniqueId] = ActiveWatcher(watcher)
    }

    private fun observeWatchers() {
        if (!isEnabled) return

        val iterator = watchers.iterator()
        while (iterator.hasNext()) {
            val (playerId, activeWatcher) = iterator.next()
            val player = Bukkit.getPlayer(playerId)
            val watcher = activeWatcher.entity
            activeWatcher.ageTicks += 2L

            val shouldRemove =
                player == null ||
                    !player.isOnline ||
                    !watcher.isValid ||
                    activeWatcher.ageTicks >= settings.lifetimeTicks ||
                    isSeen(player, watcher)

            if (shouldRemove) {
                watcher.remove()
                iterator.remove()
            }
        }
    }

    private fun isSeen(player: Player, watcher: ArmorStand): Boolean {
        val eye = player.eyeLocation
        val target = watcher.eyeLocation
        if (!MathUtil.isWithinFieldOfView(eye, target, settings.fieldOfViewDegrees)) return false
        return MathUtil.hasClearSight(eye, target)
    }

    private fun placeTemporaryTorch(player: Player) {
        val base =
            MathUtil.behindLocation(
                player,
                Random.nextDouble(
                    settings.minDistance,
                    settings.maxDistance.coerceAtLeast(settings.minDistance + 0.01),
                ),
            )
        val candidate = findTorchLocation(base) ?: return
        if (MathUtil.isWithinFieldOfView(
                player.eyeLocation,
                candidate.clone().add(0.5, 0.5, 0.5),
                settings.fieldOfViewDegrees,
            )
        ) {
            return
        }

        val block = candidate.block
        val torch = TemporaryTorch(player.uniqueId, block.location)
        if (!temporaryTorches.add(torch)) return

        player.sendBlockChange(torch.location, Material.REDSTONE_TORCH.createBlockData())
        plugin.runTrackedTaskLater(taskIds, settings.environmentChange.restoreAfterTicks) {
            restoreTorch(torch)
        }
    }

    private fun findTorchLocation(origin: Location): Location? {
        val world = origin.world
        if (!world.isChunkLoaded(origin.blockX shr 4, origin.blockZ shr 4)) return null
        val centerY = origin.blockY
        for (offset in 0..5) {
            val yOffsets = if (offset == 0) listOf(0) else listOf(offset, -offset)
            for (yOffset in yOffsets) {
                val block = world.getBlockAt(origin.blockX, centerY + yOffset, origin.blockZ)
                if (block.type.isAir && block.getRelative(0, -1, 0).type.isSolid) return block.location
            }
        }
        return null
    }

    private fun restoreTorch(torch: TemporaryTorch) {
        if (!temporaryTorches.remove(torch)) return
        val player = Bukkit.getPlayer(torch.playerId) ?: return
        val location = torch.location
        if (player.isOnline && player.world == location.world &&
            location.world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)
        ) {
            player.sendBlockChange(location, location.block.blockData)
        }
    }

    private data class ActiveWatcher(val entity: ArmorStand, var ageTicks: Long = 0L)

    private data class TemporaryTorch(val playerId: UUID, val location: Location)
}
