package com.ququla89.digitalhorror.feature

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.PacketContainer
import com.ququla89.digitalhorror.config.HallucinationSettings
import com.ququla89.digitalhorror.util.randomOrNull
import com.ququla89.digitalhorror.util.rollChance
import com.ququla89.digitalhorror.util.toHorrorComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.data.Openable
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
        val fakeData = block.blockData.clone() as Openable
        fakeData.isOpen = true
        player.sendBlockChange(block.location, fakeData)
        activeDoors[player.uniqueId] = DoorIllusion(block.location.clone())
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
                player?.takeIf(Player::isOnline)?.sendBlockChange(
                    illusion.location,
                    illusion.location.block.blockData,
                )
                iterator.remove()
            }
        }
    }

    private fun restoreDoor(playerId: UUID) {
        val illusion = activeDoors.remove(playerId) ?: return
        Bukkit.getPlayer(playerId)?.takeIf(Player::isOnline)?.sendBlockChange(
            illusion.location,
            illusion.location.block.blockData,
        )
    }

    private fun findRandomClosedDoor(player: Player): Block? {
        val radius = settings.door.searchRadius
        val center = player.location
        val random = ThreadLocalRandom.current()
        var selected: Block? = null
        var matches = 0

        for (x in -radius..radius) {
            for (y in -4..4) {
                for (z in -radius..radius) {
                    val block = center.world.getBlockAt(center.blockX + x, center.blockY + y, center.blockZ + z)
                    val data = block.blockData
                    if (data is Openable && !data.isOpen) {
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

        val restoreTask =
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable {
                    if (player.isOnline) restoreHeldItem(player)
                    activeItemGlitches.remove(player.uniqueId)
                },
                settings.heldItem.durationTicks,
            )
        taskIds += restoreTask.taskId
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

    private data class DoorIllusion(val location: Location, var ageTicks: Long = 0L)

    private companion object {
        const val NANOS_PER_TICK = 50_000_000L
    }
}
