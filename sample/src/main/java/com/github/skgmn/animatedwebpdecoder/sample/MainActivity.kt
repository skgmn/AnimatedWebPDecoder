package com.github.skgmn.animatedwebpdecoder.sample

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.compose.LocalImageLoader
import coil.compose.rememberImagePainter
import coil.util.DebugLogger
import com.github.skgmn.animatedwebpdecoder.sample.ui.theme.AnimatedWebpDecoderTheme
import com.github.skgmn.webpdecoder.WebPDecoder

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalCoilApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val imageLoader = ImageLoader.Builder(this)
                .componentRegistry {
                    add(WebPDecoder(true))
                }
                .build()
            CompositionLocalProvider(
                LocalImageLoader provides imageLoader
            ) {
                AnimatedWebpDecoderTheme {
                    Surface(color = MaterialTheme.colors.background) {
                        val imagePainter = rememberImagePainter(
                            "android.resource://$packageName/" + R.drawable.animated_webp_sample
                        )
                        Image(
                            painter = imagePainter,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AnimatedWebpDecoderTheme {
        Greeting("Android")
    }
}