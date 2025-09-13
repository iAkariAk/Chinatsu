package io.github.iakariak.chinatsu.example

import io.github.iakariak.chinatsu.annotation.*
import io.github.iakariak.chinatsu.config.Comment
import io.github.iakariak.chinatsu.config.Config
import io.github.iakariak.chinatsu.config.Configs
import io.github.iakariak.chinatsu.example.Yoshikawa.Companion.logger
import kotlinx.serialization.Serializable
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import org.slf4j.LoggerFactory


@ChinatsuApp
open class Yoshikawa : ModInitializer {
    override fun onInitialize() = Unit

    companion object {
        val logger = LoggerFactory.getLogger("YoshikawaChinatsu")!!
    }
}

@Init
fun commonSetup() {
    val config by YoConfigs
    logger.info("YoshikawaChinatsu is launching with ${config.name}")
}

@Init
fun onChat() = ClientSendMessageEvents.CHAT.register { message ->
    logger.info("[CHAT]: $message")
}

@Listen("net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.CHAT")
fun onChat2(message: String) {
    logger.info("[CHAT] [LISTEN]: $message")
}

typealias AgeInt = @WithinInt(1, 18) Int

@AutoCodec
@AutoStreamCodec
@Serializable
data class YoConfig(
    @Comment("How to name you desu.")
    val name: String = "Akari",
    @Comment("Less than 18 will be banned.")
    val age: AgeInt? = 13,
    @WithinLong(1, Long.MAX_VALUE)
    val def: Long? = null,
    @DelegateCodec
    val fullname: kotlin.Pair<String, String?> = "yoshikawa" to "chinatsu",
) : Config {
    companion object
}

@CConfigs
object YoConfigs : Configs<YoConfig>("yoshikawa_chinatsu", YoConfig.serializer()) {
    override val default = YoConfig()
}
