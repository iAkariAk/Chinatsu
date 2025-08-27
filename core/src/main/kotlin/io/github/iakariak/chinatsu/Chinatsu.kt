package io.github.iakariak.chinatsu

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory


open class Chinatsu : ModInitializer {
    override fun onInitialize() = Unit

    companion object {
        val logger = LoggerFactory.getLogger("Chinatsu")!!
    }
}

