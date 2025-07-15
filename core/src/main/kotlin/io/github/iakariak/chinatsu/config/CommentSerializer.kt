@file:OptIn(ExperimentalSerializationApi::class)

package io.github.iakariak.chinatsu.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@SerialInfo
annotation class Comment(val content: String)

open class CommentSerializer<T>(private val delegate: KSerializer<T>) : KSerializer<T> {
    override val descriptor =
        SerialDescriptor("com.io.github.iakariak.dashboard.util.config.CommentSerializer", delegate.descriptor)

    override fun serialize(encoder: Encoder, value: T) {
        encoder as JsonEncoder
        val json = encoder.json
        val element = json.encodeToJsonElement(delegate, value)
        require(element is JsonObject)

        fun commentRecursively(jsonElement: JsonElement, descriptor: SerialDescriptor): JsonElement {
            if (jsonElement is JsonArray) {
                return JsonArray(jsonElement.map { commentRecursively(it, descriptor.elementDescriptors.first()) })
            }
            if (jsonElement !is JsonObject) return jsonElement
            val obj = jsonElement.toMutableMap()
            List(descriptor.elementsCount) {
                Triple(
                    descriptor.getElementName(it),
                    descriptor.getElementAnnotations(it),
                    descriptor.getElementDescriptor(it)
                )
            }.asSequence()
                .filter { (name, _, _) -> name in obj }
                .onEach { (name, _, descriptor) ->
                     obj[name] = commentRecursively(obj[name]!!, descriptor)
                }
                .mapNotNull { (name, annotations, child) ->
                    annotations.find { it is Comment }?.let {
                        Triple(name, (it as Comment).content, child)
                    }
                }
                .forEach { (name, content, child) ->
                    obj["#$name"] = JsonPrimitive(content)
                }
            return JsonObject(obj)
        }

        val commented = commentRecursively(element, descriptor)
        encoder.encodeJsonElement(commented)
    }

    override fun deserialize(decoder: Decoder): T = delegate.deserialize(decoder)
}