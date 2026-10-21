package com.ququla89.digitalhorror.feature

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.PacketContainer
import com.ququla89.digitalhorror.config.TabGlitchSettings
import com.ququla89.digitalhorror.util.randomOrNull
import com.ququla89.digitalhorror.util.rollChance
import com.ququla89.digitalhorror.util.runTrackedTaskLater
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

        runCatching {
            val nmsPacket = createAddPlayerPacket(fakeId, fakeName)
            protocolManager.sendServerPacket(player, PacketContainer.fromPacket(nmsPacket))
            visibleEntries.getOrPut(player.uniqueId, ::mutableSetOf) += fakeId
        }.onFailure { error ->
            plugin.logger.warning("Tab リスト演出の送信に失敗しました: ${error.message}")
            return
        }

        plugin.runTrackedTaskLater(taskIds, settings.visibleDurationTicks) {
            try {
                if (player.isOnline) removeFakeEntry(player, fakeId)
            } finally {
                visibleEntries[player.uniqueId]?.remove(fakeId)
                if (visibleEntries[player.uniqueId]?.isEmpty() == true) visibleEntries.remove(player.uniqueId)
            }
        }
    }

    private fun createAddPlayerPacket(fakeId: UUID, fakeName: String): Any {
        val packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket")
        val entryClass = Class.forName("${packetClass.name}\$Entry")
        val actionClass = Class.forName("${packetClass.name}\$Action")
        val profileClass = Class.forName("com.mojang.authlib.GameProfile")
        val gameTypeClass = Class.forName("net.minecraft.world.level.GameType")
        val componentClass = Class.forName("net.minecraft.network.chat.Component")

        val profile =
            profileClass
                .getConstructor(UUID::class.java, String::class.java)
                .newInstance(fakeId, fakeName.take(16))
        val gameMode = gameTypeClass.getField("SPECTATOR").get(null)
        val displayName = componentClass.getMethod("literal", String::class.java).invoke(null, fakeName)
        val entryConstructor =
            entryClass.constructors.firstOrNull { it.parameterCount == 9 }
                ?: entryClass.constructors.firstOrNull { it.parameterCount == 7 }
                ?: error("Unsupported ClientboundPlayerInfoUpdatePacket.Entry constructor")
        val entryArguments =
            when (entryConstructor.parameterCount) {
                9 -> arrayOf(fakeId, profile, true, 999, gameMode, displayName, true, 0, null)
                7 -> arrayOf(fakeId, profile, true, 999, gameMode, displayName, null)
                else -> error("Unsupported ClientboundPlayerInfoUpdatePacket.Entry constructor")
            }
        val entry = entryConstructor.newInstance(*entryArguments)
        val addPlayerAction = actionClass.getField("ADD_PLAYER").get(null)
        val actions = EnumSet::class.java.getMethod("of", Enum::class.java).invoke(null, addPlayerAction)
        val packetConstructor = packetClass.getConstructor(EnumSet::class.java, List::class.java)

        return packetConstructor.newInstance(actions, listOf(entry))
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
