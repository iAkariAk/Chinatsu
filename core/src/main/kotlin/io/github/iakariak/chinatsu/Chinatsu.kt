package io.github.iakariak.chinatsu

import io.github.iakariak.chinatsu.Chinatsu.Companion.logger
import io.github.iakariak.chinatsu.annotation.*
import io.github.iakariak.chinatsu.config.Comment
import io.github.iakariak.chinatsu.config.Config
import io.github.iakariak.chinatsu.config.Configs
import kotlinx.serialization.Serializable
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
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

@Listen("net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.CHAT")
fun onChatDesu(message: String) {
    Chinatsu.logger.info("[CHAT] [LISTEN]: $message")
}

@Init
fun onChat() = ClientSendMessageEvents.CHAT.register { message ->
    Chinatsu.logger.info("[CHAT]: $message")
}


@AutoCodec
@AutoStreamCodec
@Serializable
data class ChinatsuConfig(
    @Comment("How to name you desu.")
    val name: String = "Akari",
    @Comment("Less than 18 will be banned desuno.")
    val age: Int? = 17
) : Config {
    companion object
}

@CConfigs
object ChinatsuConfigs : Configs<ChinatsuConfig>("chinatsu", ChinatsuConfig.serializer()) {
    override val default = ChinatsuConfig()
}
