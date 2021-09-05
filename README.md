# Introduction

An implementation of [Coil](https://github.com/coil-kt/coil)'s `Decoder` to support animated WebP on Android of which sdk version is less than 28. It depends on _libwebp_ as a native library.

# Setup

In your `settings.gradle`

```gradle
dependencyResolutionManagement {
    repositories {
        maven {
            url "https://maven.pkg.github.com/skgmn/AnimatedWebPDecoder"
            credentials {
                username <Your GitHub ID>
                password <Your GitHub Personal Access Token>
            }
        }
    }
}
```

In your `app/build.gradle`

```gradle
dependencies {
    implementation "com.github.skgmn:animatedwebpdecoder:0.1.0"
}
```

# How to use

Simply add `AnimatedWebPDecoder` to your `ImageLoader.Builder`. As stated in [here](https://coil-kt.github.io/coil/gifs/), it is recommended to use with `io.coil-kt:coil-gif`.

```kotlin
val imageLoader = ImageLoader.Builder(context)
    .componentRegistry {
        if (SDK_INT >= 28) {
            add(ImageDecoderDecoder(context))
        } else {
            add(AnimatedWebPDecoder())
        }
    }
    .build()
```

# Proguard rules

As it uses native library, it also needs proguard rules. Add these rules to your `proguard-rules.pro`.

```
-keep class com.github.skgmn.webpdecoder.libwebp.LibWebPAnimatedDecoder {
    java.nio.ByteBuffer byteBuffer;
    native <methods>;
}

-keep class com.github.skgmn.webpdecoder.libwebp.LibWebPAnimatedDecoder$Metadata {
    *;
}
```
