package com.ququla89.digitalhorror

import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import com.ququla89.digitalhorror.config.ConfigManager
import com.ququla89.digitalhorror.feature.AudioAnomalyFeature
import com.ququla89.digitalhorror.feature.HallucinationFeature
import com.ququla89.digitalhorror.feature.HorrorFeature
import com.ququla89.digitalhorror.feature.SystemDistortionFeature
import com.ququla89.digitalhorror.feature.TabGlitchFeature
import com.ququla89.digitalhorror.feature.UnseenWatcherFeature
import org.bukkit.plugin.java.JavaPlugin

class DigitalHorrorPlugin : JavaPlugin() {
    lateinit var configManager: ConfigManager
        private set

    lateinit var protocolManager: ProtocolManager
        private set

    private val features = mutableListOf<HorrorFeature>()

    override fun onEnable() {
        saveDefaultConfig()
        configManager = ConfigManager(this).also(ConfigManager::load)
        protocolManager = ProtocolLibrary.getProtocolManager()

        val settings = configManager.settings
        features +=
            listOf(
                SystemDistortionFeature(this, settings.systemDistortion),
                TabGlitchFeature(this, protocolManager, settings.tabGlitch),
                UnseenWatcherFeature(this, settings.unseenWatcher),
                AudioAnomalyFeature(this, settings.audioAnomaly),
                HallucinationFeature(this, protocolManager, settings.hallucination),
            )

        features.forEach(HorrorFeature::enable)
        logger.info("Digital Horror を有効化しました。")
    }

    override fun onDisable() {
        features.asReversed().forEach(HorrorFeature::disable)
        features.clear()
        logger.info("Digital Horror を無効化し、演出を復元しました。")
    }
}
