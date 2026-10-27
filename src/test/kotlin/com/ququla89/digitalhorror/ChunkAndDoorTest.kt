package com.ququla89.digitalhorror

import com.comphenix.protocol.ProtocolManager
import com.ququla89.digitalhorror.config.ConfigManager
import com.ququla89.digitalhorror.feature.AudioAnomalyFeature
import com.ququla89.digitalhorror.feature.HallucinationFeature
import com.ququla89.digitalhorror.feature.UnseenWatcherFeature
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.type.Door
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.EntityEquipment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.Test
import kotlin.test.assertNull

class ChunkAndDoorTest {
    private class Fixture {
        val plugin = mock(JavaPlugin::class.java)
        val world = mock(World::class.java)
        val player = mock(Player::class.java)
        val config = YamlConfiguration().apply {
            set("hallucination.door.search-radius", 1)
            set("hallucination.door.maximum-duration-ticks", 5L)
        }
        val settings = ConfigManager(plugin).also {
            `when`(plugin.config).thenReturn(config)
            it.load()
        }.settings
        val watcher = UnseenWatcherFeature(plugin, settings.unseenWatcher)
        val audio = AudioAnomalyFeature(plugin, settings.audioAnomaly)
        val hallucination = HallucinationFeature(plugin, mock(ProtocolManager::class.java), settings.hallucination)

        init {
            `when`(player.uniqueId).thenReturn(UUID.randomUUID())
            `when`(player.isOnline).thenReturn(true)
            `when`(player.world).thenReturn(world)
            `when`(player.location).thenReturn(Location(world, -0.5, 64.0, -0.5))
            `when`(player.eyeLocation).thenReturn(Location(world, -0.5, 65.6, -0.5))
        }
    }

    private fun invoke(target: Any, name: String, argument: Any, type: Class<*> = Player::class.java): Any? =
        target.javaClass.getDeclaredMethod(name, type).apply { isAccessible = true }.invoke(target, argument)

    @Test
    fun `unloaded chunks are rejected without requesting blocks or chunks`() {
        val f = Fixture()
        assertNull(invoke(f.audio, "findPhantomMinerSource", f.player))
        invoke(f.watcher, "spawnWatcher", f.player)
        assertNull(invoke(f.watcher, "findTorchLocation", Location(f.world, -0.5, 64.0, -0.5), Location::class.java))
        assertNull(invoke(f.hallucination, "findRandomClosedDoor", f.player))
        verify(f.world, never()).getBlockAt(anyInt(), anyInt(), anyInt())
        verify(f.world, never()).getBlockAt(any(Location::class.java))
        verify(f.world, never()).getChunkAt(anyInt(), anyInt())
        verify(f.world, never()).getChunkAt(any(Location::class.java))
    }

    @Test
    fun `door scan skips unloaded neighboring chunks including negative coordinates`() {
        val f = Fixture()
        val block = mock(Block::class.java)
        `when`(block.blockData).thenReturn(mock(BlockData::class.java))
        `when`(f.world.isChunkLoaded(-1, -1)).thenReturn(true)
        `when`(f.world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer {
            val x = it.getArgument<Int>(0)
            val z = it.getArgument<Int>(2)
            check(x < 0 && z < 0) { "Unloaded chunk was scanned" }
            block
        }
        assertNull(invoke(f.hallucination, "findRandomClosedDoor", f.player))
        verify(f.world, never()).getChunkAt(anyInt(), anyInt())
    }

    @Test
    fun `door illusion changes both halves and restores live data on expiry and disable`() {
        for (disable in listOf(false, true)) {
            val f = Fixture()
            val bottom = mock(Block::class.java)
            val top = mock(Block::class.java)
            val ordinary = mock(Block::class.java)
            `when`(ordinary.blockData).thenReturn(mock(BlockData::class.java))
            val bottomData = mock(Door::class.java)
            val topData = mock(Door::class.java)
            val fakeBottom = mock(Door::class.java)
            val fakeTop = mock(Door::class.java)
            val bottomLocation = Location(f.world, -1.0, 64.0, -1.0)
            val topLocation = Location(f.world, -1.0, 65.0, -1.0)
            `when`(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true)
            `when`(bottom.blockData).thenReturn(bottomData)
            `when`(top.blockData).thenReturn(topData)
            `when`(bottomData.half).thenReturn(Bisected.Half.BOTTOM)
            `when`(topData.half).thenReturn(Bisected.Half.TOP)
            `when`(bottomData.clone()).thenReturn(fakeBottom)
            `when`(topData.clone()).thenReturn(fakeTop)
            `when`(bottom.getRelative(0, 1, 0)).thenReturn(top)
            `when`(bottom.location).thenReturn(bottomLocation)
            `when`(top.location).thenReturn(topLocation)
            `when`(f.world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer {
                when {
                    it.getArgument<Int>(0) != -1 || it.getArgument<Int>(2) != -1 -> ordinary
                    it.getArgument<Int>(1) == 64 -> bottom
                    it.getArgument<Int>(1) == 65 -> top
                    else -> ordinary
                }
            }
            `when`(f.world.getBlockAt(any(Location::class.java))).thenAnswer {
                val location = it.getArgument<Location>(0)
                if (location.blockY == 64) bottom else top
            }
            mockStatic(Bukkit::class.java).use { bukkit ->
                bukkit.`when`<Player> { Bukkit.getPlayer(f.player.uniqueId) }.thenReturn(f.player)
                bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(mock(BukkitScheduler::class.java))
                invoke(f.hallucination, "showOpenDoor", f.player)
                verify(fakeBottom).isOpen = true
                verify(fakeTop).isOpen = true
                verify(f.player).sendBlockChange(bottomLocation, fakeBottom)
                verify(f.player).sendBlockChange(topLocation, fakeTop)
                val liveBottom = mock(BlockData::class.java)
                val liveTop = mock(BlockData::class.java)
                `when`(bottom.blockData).thenReturn(liveBottom)
                `when`(top.blockData).thenReturn(liveTop)
                HallucinationFeature::class.java.getDeclaredField("isEnabled").apply {
                    isAccessible = true
                }.set(f.hallucination, true)
                if (disable) {
                    f.hallucination.disable()
                } else {
                    HallucinationFeature::class.java.getDeclaredMethod("updateDoorIllusions").apply {
                        isAccessible = true
                    }.invoke(f.hallucination)
                }
                verify(f.player).sendBlockChange(bottomLocation, liveBottom)
                verify(f.player).sendBlockChange(topLocation, liveTop)
                verify(bottom, never()).setBlockData(any(BlockData::class.java))
                verify(top, never()).setBlockData(any(BlockData::class.java))
                verify(f.world, never()).getChunkAt(anyInt(), anyInt())
            }
        }
    }

    @Test
    fun `watcher is nonpersistent before it is spawned`() {
        val f = Fixture()
        val block = mock(Block::class.java)
        val stand = mock(ArmorStand::class.java)
        `when`(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true)
        `when`(f.world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block)
        `when`(f.world.getBlockAt(any(Location::class.java))).thenReturn(block)
        `when`(block.isPassable).thenReturn(true)
        `when`(stand.equipment).thenReturn(mock(EntityEquipment::class.java))
        doAnswer {
            it.getArgument<Consumer<ArmorStand>>(2).accept(stand)
            stand
        }.`when`(f.world).spawn(any(Location::class.java), eq(ArmorStand::class.java), any<Consumer<ArmorStand>>())
        mockConstruction(ItemStack::class.java) { item, _ ->
            `when`(item.itemMeta).thenReturn(mock(SkullMeta::class.java))
        }.use {
            invoke(f.watcher, "spawnWatcher", f.player)
        }
        verify(stand).isPersistent = false
        verify(f.world, never()).getChunkAt(anyInt(), anyInt())
    }
}
