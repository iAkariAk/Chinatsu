package io.github.iakakariak.chinatsu.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class AutoCodec(val name: String = "CODEC")

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class AutoStreamCodec(val name: String = "STREAM_CODEC")


/**
 * To specify how to codec your property
 *
 * @param name There is placeholder ~
 * ~ will be replaced into property's name
 * @param codec There are placeholders: ~ and ^,
 * ~ will be replaced into qualified name
 * ^ will be replaced into either CODEC or STREAM_CODEC based on where you use the annotation
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class CodecInfo(val name: String = "~", val codec: String = "~.^")