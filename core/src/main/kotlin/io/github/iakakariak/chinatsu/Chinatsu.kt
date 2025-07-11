package io.github.iakakariak.chinatsu

import io.github.iakakariak.chinatsu.Chinatsu.Companion.logger
import io.github.iakakariak.chinatsu.annotation.*
import io.github.iakakariak.chinatsu.config.Comment
import io.github.iakakariak.chinatsu.config.Config
import io.github.iakakariak.chinatsu.config.Configs
import kotlinx.serialization.Serializable
import net.fabricmc.api.ModInitializer
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
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

@SubscribeEvent("PlayerPickItemEvents.BLOCK")
fun onPick(player: ServerPlayer, pos: BlockPos, state: BlockState, requestIncludeData: Boolean): ItemStack {
    println(player.name)
    return player.mainHandItem
}

//@AutoCodec
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
