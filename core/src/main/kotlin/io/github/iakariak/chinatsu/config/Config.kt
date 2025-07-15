@file:OptIn(ExperimentalSerializationApi::class)

package io.github.iakariak.chinatsu.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.reflect.KProperty

val ConfigJson = Json {
    encodeDefaults = true
    allowComments = true
    explicitNulls = true
    ignoreUnknownKeys = true
    prettyPrint = true
    prettyPrintIndent = "    "
}

interface Config

interface ConfigFormation {
    fun <C : Config> encode(serializer: KSerializer<C>, value: C, path: Path)
    fun <C : Config> decode(serializer: KSerializer<C>, path: Path): C
    open class Json(private val json: kotlinx.serialization.json.Json) : ConfigFormation {
        companion object : Json(ConfigJson)

        override fun <C : Config> encode(serializer: KSerializer<C>, value: C, path: Path) =
            path.writeText(json.encodeToString(serializer, value))

        override fun <C : Config> decode(serializer: KSerializer<C>, path: Path): C =
            json.decodeFromString(serializer, path.readText())
    }
}

abstract class Configs<C : Config>(val name: String, serializer: KSerializer<C>) {
    abstract val default: C
    private val commentSerializer = CommentSerializer(serializer)
    private val formation: ConfigFormation = ConfigFormation.Json

    private val config by lazy {
        val path = FabricLoader.getInstance().configDir.resolve("$name.json")
        if (path.notExists()) {
            formation.encode(commentSerializer, default, path)
            return@lazy default
        }

        formation.decode(commentSerializer, path)
    }


    fun get(): C = config

    operator fun getValue(thisRef: Any?, kproperty: KProperty<*>): C = get()
}
