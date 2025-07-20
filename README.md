# Chinatsu

## Introduction

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
) {
    companion object  // KSP cannot modify source code.
}
```

## Usage

Ensure you add dependency,
i.e. following code was added in your build-script
```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.iakariak")
    modImplementation("...")
}
```

And refer to the docs dokka generate(TODO)
