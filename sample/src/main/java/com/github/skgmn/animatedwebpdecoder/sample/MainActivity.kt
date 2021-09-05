package com.github.skgmn.animatedwebpdecoder.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.compose.LocalImageLoader
import coil.compose.rememberImagePainter
import com.github.skgmn.animatedwebpdecoder.sample.ui.theme.AnimatedWebpDecoderTheme
import com.github.skgmn.webpdecoder.AnimatedWebPDecoder

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalCoilApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val imageLoader = ImageLoader.Builder(this)
                .componentRegistry {
                    add(AnimatedWebPDecoder())
                }
                .build()
            CompositionLocalProvider(
                LocalImageLoader provides imageLoader
            ) {
                AnimatedWebpDecoderTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background)
                    ) {
                        Image(
                            painter = rememberImagePainter(
                                "android.resource://$packageName/" + R.drawable.animated_webp_sample
                            ),
                            contentDescription = null
                        )
                        Image(
                            painter = rememberImagePainter(
                                "android.resource://$packageName/" + R.drawable.animated_webp_sample_2
                            ),
                            contentDescription = null
                        )
                        Image(
                            painter = rememberImagePainter(
                                "android.resource://$packageName/" + R.drawable.animated_webp_sample_3
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}