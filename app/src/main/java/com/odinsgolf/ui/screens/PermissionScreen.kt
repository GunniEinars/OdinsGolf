package com.odinsgolf.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.odinsgolf.ui.theme.OdinOnDim

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Scaffold {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text("Location needed", style = MaterialTheme.typography.title3, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "OdinsGolf uses GPS to show distances to the green. Nothing leaves your watch.",
                color = OdinOnDim,
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Chip(
                colors = ChipDefaults.primaryChipColors(),
                onClick = onRequest,
                label = { Text("Grant location") },
            )
        }
    }
}
