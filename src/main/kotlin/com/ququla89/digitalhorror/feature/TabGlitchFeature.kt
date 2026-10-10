package com.ququla89.digitalhorror.feature

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.PlayerInfoData
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedGameProfile
import com.ququla89.digitalhorror.config.TabGlitchSettings
import com.ququla89.digitalhorror.util.randomOrNull
import com.ququla89.digitalhorror.util.rollChance
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.EnumSet
import java.util.UUID

class TabGlitchFeature(
    private val plugin: JavaPlugin,
    private val protocolManager: ProtocolManager,
    private val settings: TabGlitchSettings,
) : HorrorFeature {
    override var isEnabled: Boolean = false
        private set

    private val taskIds = mutableSetOf<Int>()
    private val visibleEntries = mutableMapOf<UUID, MutableSet<UUID>>()

    override fun enable() {
        if (isEnabled || !settings.enabled) return
        isEnabled = true

        val task =
            Bukkit.getScheduler().runTaskTimer(
                plugin,
                Runnable(::runGlitchCheck),
                settings.checkIntervalTicks,
                settings.checkIntervalTicks,
            )
        taskIds += task.taskId
    }

    override fun disable() {
        if (!isEnabled) return
        isEnabled = false
        taskIds.forEach(Bukkit.getScheduler()::cancelTask)
        taskIds.clear()

        Bukkit.getOnlinePlayers().forEach { player ->
            visibleEntries.remove(player.uniqueId)?.forEach { fakeId -> removeFakeEntry(player, fakeId) }
        }
        visibleEntries.clear()
    }

    private fun runGlitchCheck() {
        if (!isEnabled) return
        Bukkit.getOnlinePlayers().forEach { player ->
            val fakeName = settings.fakeNames.randomOrNull()
            if (fakeName != null && settings.chance.rollChance()) addFakeEntry(player, fakeName)
        }
    }

    private fun addFakeEntry(player: Player, fakeName: String) {
        val fakeId = UUID.randomUUID()
        val profile = WrappedGameProfile(fakeId, fakeName.take(16))
        val data =
            PlayerInfoData(
                fakeId,
                999,
                true,
                EnumWrappers.NativeGameMode.SPECTATOR,
                profile,
                WrappedChatComponent.fromText(fakeName),
            )

        runCatching {
            val packet = PacketContainer(PacketType.Play.Server.PLAYER_INFO)
            packet.playerInfoActions.write(0, EnumSet.of(EnumWrappers.PlayerInfoAction.ADD_PLAYER))
            val lists = packet.playerInfoDataLists
            lists.write((lists.size() - 1).coerceAtLeast(0), listOf(data))
            protocolManager.sendServerPacket(player, packet)
            visibleEntries.getOrPut(player.uniqueId, ::mutableSetOf) += fakeId
        }.onFailure { error ->
            plugin.logger.warning("Tab リスト演出の送信に失敗しました: ${error.message}")
            return
        }

        val removalTask =
            Bukkit.getScheduler().runTaskLater(
                plugin,
                Runnable {
                    if (player.isOnline) removeFakeEntry(player, fakeId)
                    visibleEntries[player.uniqueId]?.remove(fakeId)
                },
                settings.visibleDurationTicks,
            )
        taskIds += removalTask.taskId
    }

    private fun removeFakeEntry(player: Player, fakeId: UUID) {
        runCatching {
            val packet = PacketContainer(PacketType.Play.Server.PLAYER_INFO_REMOVE)
            packet.uuidLists.writeSafely(0, listOf(fakeId))
            protocolManager.sendServerPacket(player, packet)
        }.onFailure { error ->
            plugin.logger.warning("Tab リスト演出の復元に失敗しました: ${error.message}")
        }
    }
}
