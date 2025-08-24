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
    val age: Int? = 17,
    @WithinLong(1, Long.MAX_VALUE)
    val def: Long? = null,
    val fullname: Pair<String, String> = "yoshikawa" to "chinatsu",
    val complex: Pair<String, Pair<@WithinInt(1, 100) Int, Map<String, @WithinDouble(
        0.0,
        1.0
    ) Double>>> = "yoshikawa" to (1 to mapOf("umezonokokami" to .5)),
    val natural: List<Pair<String?, Pair<String?, Pair<String?, Pair<String?, Pair<String?, Pair<List<Pair<Int?, Long?>>, Double?>>>>>>> = emptyList(),
    val _1: @WithinInt(1,1) Int = 1,
    val _2: @WithinInt(2,2) Int = 2,
    val _3: @WithinInt(3,3) Int = 3
) : Config {
    companion object
}

@CConfigs
object ChinatsuConfigs : Configs<ChinatsuConfig>("chinatsu", ChinatsuConfig.serializer()) {
    override val default = ChinatsuConfig()
}
