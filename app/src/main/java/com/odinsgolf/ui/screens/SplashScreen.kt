package com.odinsgolf.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.odinsgolf.R

/**
 * Opening screen: the full OdinsGolf logo on its white field. Rendered as a
 * full-screen image (not a circular icon) so the whole logo — emblem and
 * wordmark — shows uncropped at a good size. Static, battery-cheap.
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.odins_logo),
            contentDescription = "OdinsGolf",
            modifier = Modifier.fillMaxSize(0.92f),
            contentScale = ContentScale.Fit,
        )
    }
}
