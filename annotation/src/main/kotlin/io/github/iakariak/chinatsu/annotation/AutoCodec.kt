/**
 * There are some annotation in which some merely are available on nether `Codec` or `StreamCodec`
 * and some are available on both.
 *
 * Essentially, annotating on the value-parameter directly
 * is the same as annotating on the outermost type
 * just a shorthand form.
 */

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
 * Available on Codec & StreamCodec
 * 
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


/**
 * Available on Codec & StreamCodec
 * 
 * To limit the value into a range
 *
 * @param  startInclusive start of range (inclusive)
 * @param  endInclusive end of range (inclusive)
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class WithinInt(
    val startInclusive: Int,
    val endInclusive: Int
)

/**
 * Available on Codec & StreamCodec
 * 
 * To limit the value into a range
 *
 * @param  startInclusive start of range (inclusive)
 * @param  endInclusive end of range (inclusive)
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class WithinLong(
    val startInclusive: Long,
    val endInclusive: Long
)

/**
 * Available on Codec & StreamCodec
 * 
 * To limit the value into a range
 *
 * @param  startInclusive start of range (inclusive)
 * @param  endInclusive end of range (inclusive)
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class WithinFloat(
    val startInclusive: Double,
    val endInclusive: Double
)

/**
 * Available on Codec & StreamCodec
 * 
 * To limit the value into a range
 *
 * @param  startInclusive start of range (inclusive)
 * @param  endInclusive end of range (inclusive)
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class WithinDouble(
    val startInclusive: Double,
    val endInclusive: Double
)

/**
 * Available on StreamCodec
 *
 * Delegate the StreamCodec into Codec via `StreamCodec.fromCodec`
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
annotation class DelegateCodec