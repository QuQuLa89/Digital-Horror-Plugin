package com.ququla89.digitalhorror.util

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable

/** Schedules a synchronous one-shot task and releases its ID even when the action fails. */
internal fun JavaPlugin.runTrackedTaskLater(taskIds: MutableSet<Int>, delay: Long, action: () -> Unit) {
    val task = object : BukkitRunnable() {
        override fun run() {
            try {
                action()
            } finally {
                taskIds.remove(taskId)
            }
        }
    }
    taskIds += task.runTaskLater(this, delay).taskId
}
