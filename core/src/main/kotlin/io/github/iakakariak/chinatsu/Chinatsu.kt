package io.github.iakakariak.chinatsu

import io.github.iakakariak.chinatsu.Chinatsu.Companion.logger
import io.github.iakakariak.chinatsu.annotation.ChinatsuApp
import io.github.iakakariak.chinatsu.annotation.Init
import io.github.iakakariak.chinatsu.config.Comment
import io.github.iakakariak.chinatsu.config.Config
import io.github.iakakariak.chinatsu.config.Configs
import kotlinx.serialization.Serializable
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

@ChinatsuApp
open class Chinatsu : ModInitializer {
    override fun onInitialize() = Unit

    companion object {
        val logger = LoggerFactory.getLogger("Chinatsu")
    }
}

@Init
fun commonSetup() {
    val config by ChinatsuConfigs
    logger.info("Chinatsu is launching with ${config.name}")
}

@Serializable
data class ChinatsuConfig(
    @Comment("How to name you desu.")
    val name: String = "Akari"
) : Config

object ChinatsuConfigs : Configs<ChinatsuConfig>("chinatsu", ChinatsuConfig.serializer()) {
    override val default = ChinatsuConfig()
}