# Chinatsu

Chinatsu is a core library providing some practiced kotlin extension.
For example, Config generating, init automatically, and `Codec` and `StreamCodec` generating.
Following is a simpled demo
```kotlin
@AutoCodec
@AutoStreamCodec
data class Encouragement(
    val target: String = "akari_desu",
    val level: Int = 100,
    @CodecInfo(name = "desc")
    val description: String = "You're cued desu!"
){
    companion object  // KSP cannot modify source code.
}
```


