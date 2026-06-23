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
 * Opening screen shown briefly on launch: the OdinsGolf emblem on the logo's
 * white field, then the app fades in. Static (no animation) to stay battery-cheap.
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
            modifier = Modifier.fillMaxSize(0.86f),
            contentScale = ContentScale.Fit,
        )
    }
}
