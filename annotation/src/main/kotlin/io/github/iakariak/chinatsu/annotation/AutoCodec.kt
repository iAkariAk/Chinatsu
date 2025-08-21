package io.github.iakariak.chinatsu.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class AutoCodec(val name: String = DEFAULT_NAME) {
    companion object {
        const val DEFAULT_NAME = "CODEC"
    }
}

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class AutoStreamCodec(val name: String = DEFAULT_NAME) {
    companion object {
        const val DEFAULT_NAME = "STREAM_CODEC"
    }
}

/**
 * To specify how to codec your property
 *
 * @param name There is placeholder ~
 * ~ will be replaced into property's name
 * @param codecCalling There are placeholders: ~ and ^,
 * ~ will be replaced into its class's qualified name
 * ^ will be replaced into either CODEC or STREAM_CODEC based on where you use the annotation
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class CodecInfo(
    val name: String = "~",
    val codecCalling: String = "~.^"
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class WithinInt(
    val startInclusive: Int,
    val endInclusive: Int
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class WithinLong(
    val startInclusive: Long,
    val endInclusive: Long
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class WithinFloat(
    val startInclusive: Double,
    val endInclusive: Double
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class WithinDouble(
    val startInclusive: Double,
    val endInclusive: Double
)