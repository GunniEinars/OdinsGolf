package com.odinsgolf

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.odinsgolf.ui.OdinsGolfApp
import com.odinsgolf.ui.RoundViewModel
import com.odinsgolf.ui.screens.PermissionScreen
import com.odinsgolf.ui.theme.OdinsGolfTheme

class MainActivity : ComponentActivity() {

    // Keeps the system splash (our logo) up briefly so it's reliably seen.
    private var keepSplashOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { keepSplashOnScreen }
        super.onCreate(savedInstanceState)
        setContent {
            OdinsGolfTheme {
                val vm: RoundViewModel = viewModel()
                val context = LocalContext.current

                fun hasLocationPermission(): Boolean =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED

                var granted by remember { mutableStateOf(hasLocationPermission()) }

                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    granted = result.values.any { it }
                    if (granted) vm.restartLocation()
                }

                // Lifecycle-aware location: start on resume, pause when not visible.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, granted) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> if (granted) vm.onResume()
                            Lifecycle.Event.ON_PAUSE -> vm.onPause()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Optional keep-screen-on, off by default.
                val state by vm.uiState.collectAsStateWithLifecycle()
                DisposableEffect(state.settings.keepScreenOn) {
                    if (state.settings.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose { }
                }

                // Release the system splash shortly after first composition.
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(700)
                    keepSplashOnScreen = false
                }

                if (granted) {
                    OdinsGolfApp(vm)
                } else {
                    PermissionScreen(onRequest = {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    })
                }
            }
        }
    }
}
