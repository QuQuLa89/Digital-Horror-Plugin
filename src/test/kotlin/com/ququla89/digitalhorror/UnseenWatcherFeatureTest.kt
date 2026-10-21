package com.ququla89.digitalhorror

import com.ququla89.digitalhorror.config.EnvironmentChangeSettings
import com.ququla89.digitalhorror.config.UnseenWatcherSettings
import com.ququla89.digitalhorror.feature.UnseenWatcherFeature
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scheduler.BukkitTask
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class UnseenWatcherFeatureTest {
    @Test
    fun `torch is sent only to its viewer and restores live world data on expiry and disable`() {
        mockStatic(Bukkit::class.java).use { bukkit ->
            val plugin = mock(JavaPlugin::class.java)
            val scheduler = mock(BukkitScheduler::class.java)
            val world = mock(World::class.java)
            val player = mock(Player::class.java)
            val block = mock(Block::class.java)
            val support = mock(Block::class.java)
            val chunk = mock(Chunk::class.java)
            val fakeTorch = mock(BlockData::class.java)
            val changedData = mock(BlockData::class.java)
            val air = mock(Material::class.java)
            val solid = mock(Material::class.java)
            `when`(air.isAir).thenReturn(true)
            `when`(solid.isSolid).thenReturn(true)
            val id = UUID.randomUUID()
            val location = Location(world, 0.0, 64.0, -5.0)
            val callbacks = mutableListOf<Runnable>()
            bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(scheduler)
            bukkit.`when`<Player?> { Bukkit.getPlayer(id) }.thenReturn(player)
            bukkit.`when`<BlockData> { Bukkit.createBlockData(Material.REDSTONE_TORCH) }.thenReturn(fakeTorch)
            `when`(player.uniqueId).thenReturn(id)
            `when`(player.world).thenReturn(world)
            `when`(player.isOnline).thenReturn(true)
            `when`(player.location).thenReturn(Location(world, 0.0, 64.0, 0.0))
            `when`(player.eyeLocation).thenReturn(Location(world, 0.0, 65.0, 0.0))
            `when`(world.getBlockAt(any(Location::class.java))).thenReturn(block)
            `when`(world.getChunkAt(any(Location::class.java))).thenReturn(chunk)
            `when`(
                world.getBlockAt(
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.anyInt(),
                ),
            ).thenReturn(block)
            `when`(
                world.getChunkAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()),
            ).thenReturn(chunk)
            `when`(chunk.isLoaded).thenReturn(true)
            `when`(block.type).thenReturn(air)
            `when`(block.location).thenReturn(location)
            `when`(block.getRelative(0, -1, 0)).thenReturn(support)
            `when`(support.type).thenReturn(solid)
            `when`(block.blockData).thenReturn(changedData)
            var nextId = 0
            doAnswer { invocation ->
                callbacks += invocation.getArgument<Runnable>(1)
                mock(BukkitTask::class.java).also { `when`(it.taskId).thenReturn(++nextId) }
            }.`when`(scheduler).runTaskLater(eq(plugin), any(Runnable::class.java), anyLong())

            val settings =
                UnseenWatcherSettings(
                    true, 20L, 0.0, 5.0, 5.0, 0.0, 90.0, 20L,
                    EnvironmentChangeSettings(
                        true,
                        1.0,
                        10L,
                    ),
                )
            val feature = UnseenWatcherFeature(plugin, settings)
            val placeTorch = UnseenWatcherFeature::class.java.getDeclaredMethod(
                "placeTemporaryTorch",
                Player::class.java,
            ).apply {
                isAccessible =
                    true
            }
            placeTorch.invoke(feature, player)
            verify(player).sendBlockChange(location, fakeTorch)
            verify(block, never()).setType(any(Material::class.java), org.mockito.ArgumentMatchers.anyBoolean())
            callbacks.single().run()
            verify(player).sendBlockChange(location, changedData)
            val ids = UnseenWatcherFeature::class.java.getDeclaredField("taskIds").apply { isAccessible = true }
            assertTrue((ids.get(feature) as Set<*>).isEmpty())

            // Disable must restore pending illusions as well as cancel their tasks.
            placeTorch.invoke(feature, player)
            UnseenWatcherFeature::class.java.getDeclaredField("isEnabled").apply {
                isAccessible = true
            }.set(feature, true)
            feature.disable()
            verify(scheduler).cancelTask(nextId)
            verify(player, org.mockito.Mockito.times(2)).sendBlockChange(location, changedData)
            assertTrue((ids.get(feature) as Set<*>).isEmpty())
            verify(block, never()).setBlockData(any(BlockData::class.java), org.mockito.ArgumentMatchers.anyBoolean())
        }
    }
}
