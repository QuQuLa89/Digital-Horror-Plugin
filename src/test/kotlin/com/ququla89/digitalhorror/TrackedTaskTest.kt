package com.ququla89.digitalhorror

import com.ququla89.digitalhorror.util.runTrackedTaskLater
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrackedTaskTest {
    @Test
    fun `completed skipped and failing one-shot tasks release ids while pending tasks remain cancellable`() {
        mockStatic(Bukkit::class.java).use { bukkit ->
            val scheduler = mock(BukkitScheduler::class.java)
            val plugin = mock(JavaPlugin::class.java)
            bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(scheduler)
            val callbacks = mutableListOf<Runnable>()
            var nextId = 0
            doAnswer { invocation ->
                callbacks += invocation.getArgument<Runnable>(1)
                val task = mock(BukkitTask::class.java)
                `when`(task.taskId).thenReturn(++nextId)
                task
            }.`when`(scheduler).runTaskLater(eq(plugin), any(Runnable::class.java), anyLong())

            val taskIds = mutableSetOf(9999)
            var executions = 0
            repeat(100) {
                plugin.runTrackedTaskLater(taskIds, 0L) { executions++ }
                assertEquals(2, taskIds.size)
                callbacks.last().run()
                assertEquals(setOf(9999), taskIds)
            }
            assertEquals(100, executions)

            plugin.runTrackedTaskLater(taskIds, 1L) { return@runTrackedTaskLater }
            callbacks.last().run()
            assertEquals(setOf(9999), taskIds)

            plugin.runTrackedTaskLater(taskIds, 1L) { error("effect failed") }
            assertFailsWith<IllegalStateException> { callbacks.last().run() }
            assertEquals(setOf(9999), taskIds)

            plugin.runTrackedTaskLater(taskIds, 10L) { executions++ }
            assertEquals(setOf(9999, nextId), taskIds)
            taskIds.forEach(scheduler::cancelTask)
            taskIds.clear()
            org.mockito.Mockito.verify(scheduler).cancelTask(nextId)
            assertEquals(100, executions)
        }
    }
}
