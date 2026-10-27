package com.ququla89.digitalhorror.feature

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.PacketContainer
import com.ququla89.digitalhorror.config.HallucinationSettings
import com.ququla89.digitalhorror.util.randomOrNull
import com.ququla89.digitalhorror.util.rollChance
import com.ququla89.digitalhorror.util.runTrackedTaskLater
import com.ququla89.digitalhorror.util.toHorrorComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Door
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class HallucinationFeature(
    private val plugin: JavaPlugin,
    private val protocolManager: ProtocolManager,
    private val settings: HallucinationSettings,
) : HorrorFeature {
    override var isEnabled: Boolean = false
        private set

    private val taskIds = mutableSetOf<Int>()
    private val activeDoors = mutableMapOf<UUID, DoorIllusion>()
    private val activeItemGlitches = mutableSetOf<UUID>()
    private val lastUnderwaterDarknessAt = mutableMapOf<UUID, Long>()

    override fun enable() {
        if (isEnabled || !settings.enabled) return
        isEnabled = true

        taskIds +=
            Bukkit.getScheduler()
                .runTaskTimer(
                    plugin,
                    Runnable(::runHallucinationCheck),
                    settings.checkIntervalTicks,
                    settings.checkIntervalTicks,
                ).taskId
        taskIds +=
            Bukkit.getScheduler()
                .runTaskTimer(plugin, Runnable(::updateDoorIllusions), 5L, 5L)
                .taskId
        if (settings.underwaterDarkness.enabled) {
            taskIds +=
                Bukkit.getScheduler()
                    .runTaskTimer(
                        plugin,
                        Runnable(::checkUnderwaterDarkness),
                        settings.underwaterDarkness.checkIntervalTicks,
                        settings.underwaterDarkness.checkIntervalTicks,
                    ).taskId
        }
    }

    override fun disable() {
        if (!isEnabled) return
        isEnabled = false
        taskIds.forEach(Bukkit.getScheduler()::cancelTask)
        taskIds.clear()

        activeDoors.keys.toList().forEach(::restoreDoor)
        activeItemGlitches.toList().forEach { playerId ->
            Bukkit.getPlayer(playerId)?.takeIf(Player::isOnline)?.let(::restoreHeldItem)
        }
        activeItemGlitches.clear()
        lastUnderwaterDarknessAt.clear()
    }

    private fun runHallucinationCheck() {
        if (!isEnabled) return
        Bukkit.getOnlinePlayers().forEach { player ->
            if (settings.door.enabled && settings.door.chance.rollChance() && player.uniqueId !in activeDoors) {
                showOpenDoor(player)
            }
            if (
                settings.heldItem.enabled &&
                settings.heldItem.chance.rollChance() &&
                player.uniqueId !in activeItemGlitches
            ) {
                glitchHeldItem(player)
            }
        }
    }

    private fun showOpenDoor(player: Player) {
        val block = findRandomClosedDoor(player) ?: return
        val data = block.blockData as Door
        val bottom = if (data.half == Bisected.Half.BOTTOM) block else block.getRelative(0, -1, 0)
        val top = bottom.getRelative(0, 1, 0)
        val bottomData = bottom.blockData as? Door ?: return
        val topData = top.blockData as? Door ?: return
        if (bottomData.half != Bisected.Half.BOTTOM || topData.half != Bisected.Half.TOP) return
        for ((half, realData) in listOf(bottom to bottomData, top to topData)) {
            val fakeData = realData.clone() as Door
            fakeData.isOpen = true
            player.sendBlockChange(half.location, fakeData)
        }
        activeDoors[player.uniqueId] = DoorIllusion(bottom.location.clone(), top.location.clone())
    }

    private fun updateDoorIllusions() {
        if (!isEnabled) return
        val iterator = activeDoors.iterator()
        while (iterator.hasNext()) {
            val (playerId, illusion) = iterator.next()
            val player = Bukkit.getPlayer(playerId)
            illusion.ageTicks += 5L
            val shouldRestore =
                player == null ||
                    !player.isOnline ||
                    player.world !== illusion.location.world ||
                    player.location.distanceSquared(illusion.location) <=
                    settings.door.restoreDistance * settings.door.restoreDistance ||
                    illusion.ageTicks >= settings.door.maximumDurationTicks

            if (shouldRestore) {
                if (player != null) revertDoor(player, illusion)
                iterator.remove()
            }
        }
    }

    private fun restoreDoor(playerId: UUID) {
        val illusion = activeDoors.remove(playerId) ?: return
        Bukkit.getPlayer(playerId)?.let { revertDoor(it, illusion) }
    }

    private fun revertDoor(player: Player, illusion: DoorIllusion) {
        val location = illusion.location
        if (!player.isOnline || player.world !== location.world ||
            !location.world.isChunkLoaded(location.blockX shr 4, location.blockZ shr 4)
        ) {
            return
        }
        for (half in listOf(location, illusion.topLocation)) {
            player.sendBlockChange(half, half.block.blockData)
        }
    }

    private fun findRandomClosedDoor(player: Player): Block? {
        val radius = settings.door.searchRadius
        val center = player.location
        val random = ThreadLocalRandom.current()
        var selected: Block? = null
        var matches = 0

        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val blockX = center.blockX + x
                val blockZ = center.blockZ + z
                if (!center.world.isChunkLoaded(blockX shr 4, blockZ shr 4)) continue
                for (y in -4..4) {
                    val block = center.world.getBlockAt(blockX, center.blockY + y, blockZ)
                    val data = block.blockData
                    if (data is Door && data.half == Bisected.Half.BOTTOM && !data.isOpen) {
                        matches++
                        if (random.nextInt(matches) == 0) selected = block
                    }
                }
            }
        }
        return selected
    }

    private fun glitchHeldItem(player: Player) {
        val original = player.inventory.itemInMainHand
        val name = settings.heldItem.names.randomOrNull() ?: return
        if (original.type.isAir) return

        val glitched = original.clone()
        glitched.itemMeta =
            glitched.itemMeta.apply {
                displayName(name.toHorrorComponent(player))
            }

        if (!sendHeldItemPacket(player, glitched)) return
        activeItemGlitches += player.uniqueId

        plugin.runTrackedTaskLater(taskIds, settings.heldItem.durationTicks) {
            try {
                if (player.isOnline) restoreHeldItem(player)
            } finally {
                activeItemGlitches.remove(player.uniqueId)
            }
        }
    }

    private fun restoreHeldItem(player: Player) {
        sendHeldItemPacket(player, player.inventory.itemInMainHand)
    }

    private fun checkUnderwaterDarkness() {
        if (!isEnabled) return
        val effect = settings.underwaterDarkness
        val now = System.nanoTime()
        val cooldownNanos = effect.cooldownTicks.coerceAtMost(Long.MAX_VALUE / NANOS_PER_TICK) * NANOS_PER_TICK
        val onlineIds = Bukkit.getOnlinePlayers().mapTo(mutableSetOf()) { it.uniqueId }
        lastUnderwaterDarknessAt.keys.removeIf { it !in onlineIds }

        Bukkit.getOnlinePlayers().forEach { player ->
            val lastTriggeredAt = lastUnderwaterDarknessAt[player.uniqueId]
            val coolingDown = lastTriggeredAt != null && now - lastTriggeredAt < cooldownNanos
            if (
                !player.isDead &&
                player.eyeLocation.block.type == Material.WATER &&
                !coolingDown &&
                effect.chance.rollChance()
            ) {
                player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.DARKNESS,
                        effect.durationTicks,
                        0,
                        true,
                        false,
                        false,
                    ),
                )
                lastUnderwaterDarknessAt[player.uniqueId] = now
            }
        }
    }

    private fun sendHeldItemPacket(player: Player, item: ItemStack): Boolean = runCatching {
        val packet = protocolManager.createPacket(PacketType.Play.Server.SET_SLOT)
        packet.modifier.writeDefaults()
        writeSlotCoordinates(packet, 36 + player.inventory.heldItemSlot)
        packet.itemModifier.writeSafely(0, item)
        protocolManager.sendServerPacket(player, packet)
    }.onFailure { error ->
        plugin.logger.warning("アイテム表示演出の送信に失敗しました: ${error.message}")
    }.isSuccess

    private fun writeSlotCoordinates(packet: PacketContainer, slot: Int) {
        val integers = packet.integers
        integers.writeSafely(0, 0)
        integers.writeSafely(1, 0)
        if (integers.size() >= 3) {
            integers.writeSafely(2, slot)
        } else {
            packet.shorts.writeSafely(0, slot.toShort())
        }
    }

    private data class DoorIllusion(val location: Location, val topLocation: Location, var ageTicks: Long = 0L)

    private companion object {
        const val NANOS_PER_TICK = 50_000_000L
    }
}
