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
 * Opening screen: the full OdinsGolf logo on its white field, drawn nearly
 * full-screen. This is the *big* brand moment — the system launch splash shows
 * the same logo (smaller, masked to a circle) only to bridge cold start without
 * a blank flash, then this takes over uncropped. Static, battery-cheap.
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
            modifier = Modifier.fillMaxSize(0.97f),
            contentScale = ContentScale.Fit,
        )
    }
}
