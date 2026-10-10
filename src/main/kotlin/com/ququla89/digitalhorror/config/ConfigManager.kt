package com.ququla89.digitalhorror.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

class ConfigManager(private val plugin: JavaPlugin) {
    lateinit var settings: DigitalHorrorSettings
        private set

    fun load() {
        plugin.reloadConfig()
        settings = plugin.config.toSettings()
    }

    private fun FileConfiguration.toSettings(): DigitalHorrorSettings = DigitalHorrorSettings(
        systemDistortion =
        SystemDistortionSettings(
            enabled = getBoolean("system-distortion.enabled", true),
            checkIntervalTicks = positiveLong("system-distortion.check-interval-ticks", 600L),
            fakeChat =
            MessageEffectSettings(
                enabled = getBoolean("system-distortion.fake-chat.enabled", true),
                chance = probability("system-distortion.fake-chat.chance", 0.08),
                messages = stringList("system-distortion.fake-chat.messages"),
            ),
            bedWarning =
            MessageEffectSettings(
                enabled = getBoolean("system-distortion.bed-warning.enabled", true),
                chance = probability("system-distortion.bed-warning.chance", 0.35),
                messages = stringList("system-distortion.bed-warning.messages"),
            ),
            actionBar =
            ActionBarSettings(
                enabled = getBoolean("system-distortion.action-bar.enabled", true),
                chance = probability("system-distortion.action-bar.chance", 0.12),
                fadeStepTicks = positiveLong("system-distortion.action-bar.fade-step-ticks", 8L),
                messages = stringList("system-distortion.action-bar.messages"),
            ),
        ),
        tabGlitch =
        TabGlitchSettings(
            enabled = getBoolean("tab-glitch.enabled", true),
            checkIntervalTicks = positiveLong("tab-glitch.check-interval-ticks", 900L),
            chance = probability("tab-glitch.chance", 0.12),
            visibleDurationTicks = positiveLong("tab-glitch.visible-duration-ticks", 30L),
            fakeNames = stringList("tab-glitch.fake-names").map { it.take(16) },
        ),
        unseenWatcher =
        UnseenWatcherSettings(
            enabled = getBoolean("unseen-watcher.enabled", true),
            checkIntervalTicks = positiveLong("unseen-watcher.check-interval-ticks", 1200L),
            spawnChance = probability("unseen-watcher.spawn-chance", 0.08),
            minDistance = positiveDouble("unseen-watcher.min-distance", 5.0),
            maxDistance = positiveDouble("unseen-watcher.max-distance", 9.0),
            verticalOffset = getDouble("unseen-watcher.vertical-offset", 0.0),
            fieldOfViewDegrees =
            getDouble("unseen-watcher.field-of-view-degrees", 95.0).coerceIn(1.0, 179.0),
            lifetimeTicks = positiveLong("unseen-watcher.lifetime-ticks", 200L),
            environmentChange =
            EnvironmentChangeSettings(
                enabled = getBoolean("unseen-watcher.environment-change.enabled", true),
                chance = probability("unseen-watcher.environment-change.chance", 0.03),
                restoreAfterTicks =
                positiveLong("unseen-watcher.environment-change.restore-after-ticks", 100L),
            ),
        ),
        audioAnomaly =
        AudioAnomalySettings(
            enabled = getBoolean("audio-anomaly.enabled", true),
            movementCheckIntervalTicks =
            positiveLong("audio-anomaly.movement-check-interval-ticks", 2L),
            delayedFootstepTicks = positiveLong("audio-anomaly.delayed-footstep-ticks", 10L),
            footstepChance = probability("audio-anomaly.footstep-chance", 0.28),
            ambientCheckIntervalTicks =
            positiveLong("audio-anomaly.ambient-check-interval-ticks", 500L),
            distantSoundChance = probability("audio-anomaly.distant-sound-chance", 0.12),
            silenceChance = probability("audio-anomaly.silence-chance", 0.04),
            minDistance = positiveDouble("audio-anomaly.min-distance", 8.0),
            maxDistance = positiveDouble("audio-anomaly.max-distance", 18.0),
            volume = positiveDouble("audio-anomaly.volume", 0.25).toFloat(),
            pitchMin = positiveDouble("audio-anomaly.pitch-min", 0.65).toFloat(),
            pitchMax = positiveDouble("audio-anomaly.pitch-max", 0.95).toFloat(),
            glassBreak =
            GlassBreakSettings(
                enabled = getBoolean("audio-anomaly.glass-break.enabled", true),
                chance = probability("audio-anomaly.glass-break.chance", 0.02),
                volume = positiveDouble("audio-anomaly.glass-break.volume", 0.6).toFloat(),
                pitch = positiveDouble("audio-anomaly.glass-break.pitch", 0.55).toFloat(),
            ),
            phantomMiner =
            PhantomMinerSettings(
                enabled = getBoolean("audio-anomaly.phantom-miner.enabled", true),
                checkIntervalTicks =
                positiveLong("audio-anomaly.phantom-miner.check-interval-ticks", 3600L),
                chance = probability("audio-anomaly.phantom-miner.chance", 0.20),
                minDistance = positiveDouble("audio-anomaly.phantom-miner.min-distance", 5.0),
                maxDistance = positiveDouble("audio-anomaly.phantom-miner.max-distance", 12.0),
                minVerticalOffset = getDouble("audio-anomaly.phantom-miner.min-vertical-offset", -4.0),
                maxVerticalOffset = getDouble("audio-anomaly.phantom-miner.max-vertical-offset", -1.0),
                approachDistance =
                positiveDouble("audio-anomaly.phantom-miner.approach-distance", 3.5),
                hitIntervalTicks =
                positiveLong("audio-anomaly.phantom-miner.hit-interval-ticks", 5L),
                breakIntervalTicks =
                positiveLong("audio-anomaly.phantom-miner.break-interval-ticks", 10L),
                hitsPerBlock = positiveInt("audio-anomaly.phantom-miner.hits-per-block", 4),
                blockCount = positiveInt("audio-anomaly.phantom-miner.block-count", 4),
                volume = positiveDouble("audio-anomaly.phantom-miner.volume", 0.45).toFloat(),
                pitch = positiveDouble("audio-anomaly.phantom-miner.pitch", 0.8).toFloat(),
            ),
        ),
        hallucination =
        HallucinationSettings(
            enabled = getBoolean("hallucination.enabled", true),
            checkIntervalTicks = positiveLong("hallucination.check-interval-ticks", 700L),
            door =
            DoorHallucinationSettings(
                enabled = getBoolean("hallucination.door.enabled", true),
                chance = probability("hallucination.door.chance", 0.10),
                searchRadius = getInt("hallucination.door.search-radius", 10).coerceIn(1, 24),
                restoreDistance = positiveDouble("hallucination.door.restore-distance", 3.0),
                maximumDurationTicks =
                positiveLong("hallucination.door.maximum-duration-ticks", 160L),
            ),
            heldItem =
            HeldItemHallucinationSettings(
                enabled = getBoolean("hallucination.held-item.enabled", true),
                chance = probability("hallucination.held-item.chance", 0.08),
                durationTicks = positiveLong("hallucination.held-item.duration-ticks", 30L),
                names = stringList("hallucination.held-item.names"),
            ),
            underwaterDarkness =
            UnderwaterDarknessSettings(
                enabled = getBoolean("hallucination.underwater-darkness.enabled", true),
                checkIntervalTicks =
                positiveLong("hallucination.underwater-darkness.check-interval-ticks", 40L),
                chance = probability("hallucination.underwater-darkness.chance", 0.12),
                durationTicks =
                positiveInt("hallucination.underwater-darkness.duration-ticks", 80),
                cooldownTicks =
                positiveLong("hallucination.underwater-darkness.cooldown-ticks", 900L),
            ),
        ),
    )

    private fun FileConfiguration.probability(path: String, fallback: Double): Double =
        getDouble(path, fallback).coerceIn(0.0, 1.0)

    private fun FileConfiguration.positiveLong(path: String, fallback: Long): Long =
        getLong(path, fallback).coerceAtLeast(1L)

    private fun FileConfiguration.positiveDouble(path: String, fallback: Double): Double =
        getDouble(path, fallback).coerceAtLeast(0.01)

    private fun FileConfiguration.positiveInt(path: String, fallback: Int): Int =
        getInt(path, fallback).coerceAtLeast(1)

    private fun FileConfiguration.stringList(path: String): List<String> =
        getStringList(path).filter(String::isNotBlank)
}

data class DigitalHorrorSettings(
    val systemDistortion: SystemDistortionSettings,
    val tabGlitch: TabGlitchSettings,
    val unseenWatcher: UnseenWatcherSettings,
    val audioAnomaly: AudioAnomalySettings,
    val hallucination: HallucinationSettings,
)

data class SystemDistortionSettings(
    val enabled: Boolean,
    val checkIntervalTicks: Long,
    val fakeChat: MessageEffectSettings,
    val bedWarning: MessageEffectSettings,
    val actionBar: ActionBarSettings,
)

data class MessageEffectSettings(val enabled: Boolean, val chance: Double, val messages: List<String>)

data class ActionBarSettings(
    val enabled: Boolean,
    val chance: Double,
    val fadeStepTicks: Long,
    val messages: List<String>,
)

data class TabGlitchSettings(
    val enabled: Boolean,
    val checkIntervalTicks: Long,
    val chance: Double,
    val visibleDurationTicks: Long,
    val fakeNames: List<String>,
)

data class UnseenWatcherSettings(
    val enabled: Boolean,
    val checkIntervalTicks: Long,
    val spawnChance: Double,
    val minDistance: Double,
    val maxDistance: Double,
    val verticalOffset: Double,
    val fieldOfViewDegrees: Double,
    val lifetimeTicks: Long,
    val environmentChange: EnvironmentChangeSettings,
)

data class EnvironmentChangeSettings(val enabled: Boolean, val chance: Double, val restoreAfterTicks: Long)

data class AudioAnomalySettings(
    val enabled: Boolean,
    val movementCheckIntervalTicks: Long,
    val delayedFootstepTicks: Long,
    val footstepChance: Double,
    val ambientCheckIntervalTicks: Long,
    val distantSoundChance: Double,
    val silenceChance: Double,
    val minDistance: Double,
    val maxDistance: Double,
    val volume: Float,
    val pitchMin: Float,
    val pitchMax: Float,
    val glassBreak: GlassBreakSettings,
    val phantomMiner: PhantomMinerSettings,
)

data class GlassBreakSettings(val enabled: Boolean, val chance: Double, val volume: Float, val pitch: Float)

data class PhantomMinerSettings(
    val enabled: Boolean,
    val checkIntervalTicks: Long,
    val chance: Double,
    val minDistance: Double,
    val maxDistance: Double,
    val minVerticalOffset: Double,
    val maxVerticalOffset: Double,
    val approachDistance: Double,
    val hitIntervalTicks: Long,
    val breakIntervalTicks: Long,
    val hitsPerBlock: Int,
    val blockCount: Int,
    val volume: Float,
    val pitch: Float,
)

data class HallucinationSettings(
    val enabled: Boolean,
    val checkIntervalTicks: Long,
    val door: DoorHallucinationSettings,
    val heldItem: HeldItemHallucinationSettings,
    val underwaterDarkness: UnderwaterDarknessSettings,
)

data class DoorHallucinationSettings(
    val enabled: Boolean,
    val chance: Double,
    val searchRadius: Int,
    val restoreDistance: Double,
    val maximumDurationTicks: Long,
)

data class HeldItemHallucinationSettings(
    val enabled: Boolean,
    val chance: Double,
    val durationTicks: Long,
    val names: List<String>,
)

data class UnderwaterDarknessSettings(
    val enabled: Boolean,
    val checkIntervalTicks: Long,
    val chance: Double,
    val durationTicks: Int,
    val cooldownTicks: Long,
)
