package com.ququla89.digitalhorror.feature

import com.ququla89.digitalhorror.config.UnseenWatcherSettings
import com.ququla89.digitalhorror.util.MathUtil
import com.ququla89.digitalhorror.util.rollChance
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.data.BlockData
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
    private val temporaryBlocks = mutableMapOf<BlockPosition, BlockData>()

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
        temporaryBlocks.keys.toList().forEach(::restoreBlock)
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
        if (!spawnLocation.chunk.isLoaded || !spawnLocation.block.isPassable) return

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
        val position = BlockPosition(block.world.uid, block.x, block.y, block.z)
        if (temporaryBlocks.containsKey(position)) return

        temporaryBlocks[position] = block.blockData.clone()
        block.setType(Material.REDSTONE_TORCH, false)
        val restoreTask =
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable { restoreBlock(position) },
                settings.environmentChange.restoreAfterTicks,
            )
        taskIds += restoreTask.taskId
    }

    private fun findTorchLocation(origin: Location): Location? {
        val world = origin.world
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

    private fun restoreBlock(position: BlockPosition) {
        val original = temporaryBlocks.remove(position) ?: return
        val world = Bukkit.getWorld(position.worldId) ?: return
        val block = world.getBlockAt(position.x, position.y, position.z)
        if (block.type == Material.REDSTONE_TORCH) block.setBlockData(original, false)
    }

    private data class ActiveWatcher(val entity: ArmorStand, var ageTicks: Long = 0L)

    private data class BlockPosition(val worldId: UUID, val x: Int, val y: Int, val z: Int)
}
