package com.ququla89.digitalhorror.feature

import com.ququla89.digitalhorror.config.SystemDistortionSettings
import com.ququla89.digitalhorror.util.randomOrNull
import com.ququla89.digitalhorror.util.rollChance
import com.ququla89.digitalhorror.util.toHorrorComponent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.plugin.java.JavaPlugin

class SystemDistortionFeature(private val plugin: JavaPlugin, private val settings: SystemDistortionSettings) :
    HorrorFeature,
    Listener {
    override var isEnabled: Boolean = false
        private set

    private val taskIds = mutableSetOf<Int>()

    override fun enable() {
        if (isEnabled || !settings.enabled) return
        isEnabled = true
        plugin.server.pluginManager.registerEvents(this, plugin)

        val task =
            Bukkit.getScheduler().runTaskTimer(
                plugin,
                Runnable(::runDistortionCheck),
                settings.checkIntervalTicks,
                settings.checkIntervalTicks,
            )
        taskIds += task.taskId
    }

    override fun disable() {
        if (!isEnabled) return
        isEnabled = false
        HandlerList.unregisterAll(this)
        taskIds.forEach(Bukkit.getScheduler()::cancelTask)
        taskIds.clear()
        Bukkit.getOnlinePlayers().forEach { it.sendActionBar(Component.empty()) }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBedEnter(event: PlayerBedEnterEvent) {
        val effect = settings.bedWarning
        val message = effect.messages.randomOrNull()
        if (!isEnabled || !effect.enabled || message == null || !effect.chance.rollChance()) return

        event.isCancelled = true
        event.player.sendMessage(message.toHorrorComponent(event.player))
    }

    private fun runDistortionCheck() {
        if (!isEnabled) return
        Bukkit.getOnlinePlayers().forEach { player ->
            val fakeChat = settings.fakeChat
            if (fakeChat.enabled && fakeChat.chance.rollChance()) {
                fakeChat.messages.randomOrNull()?.let { message ->
                    player.sendMessage(
                        Component.text("<${player.name}> ", NamedTextColor.WHITE)
                            .append(message.toHorrorComponent(player)),
                    )
                }
            }

            val actionBar = settings.actionBar
            if (actionBar.enabled && actionBar.chance.rollChance()) {
                actionBar.messages.randomOrNull()?.let { showGlitchedActionBar(player, it) }
            }
        }
    }

    private fun showGlitchedActionBar(player: org.bukkit.entity.Player, rawMessage: String) {
        val base = rawMessage.toHorrorComponent(player)
        val frames =
            listOf(
                base.color(NamedTextColor.WHITE),
                base.color(NamedTextColor.GRAY),
                base.color(NamedTextColor.DARK_GRAY),
                Component.empty(),
            )

        frames.forEachIndexed { index, frame ->
            val task =
                Bukkit.getScheduler().runTaskLater(
                    plugin,
                    Runnable {
                        if (isEnabled && player.isOnline) player.sendActionBar(frame)
                    },
                    settings.actionBar.fadeStepTicks * index,
                )
            taskIds += task.taskId
        }
    }
}
