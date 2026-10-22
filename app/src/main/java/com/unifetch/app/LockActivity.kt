package com.unifetch.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import com.unifetch.app.ui.common.LocalDarkTheme
import com.unifetch.app.ui.common.SettingsProvider
import com.unifetch.app.ui.page.lock.LockScreen
import com.unifetch.app.ui.theme.SealTheme
import com.unifetch.app.util.AppLock

/**
 * The app lock screen. Opens on top of UniFetch (and any dialog it has open) while the app is
 * locked, and closes itself once unlocked.
 */
class LockActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!AppLock.locked.value) {
            finish()
            return
        }
        setContent {
            SettingsProvider(calculateWindowSizeClass(this).widthSizeClass) {
                SealTheme(
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                    isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                ) {
                    val locked by AppLock.locked.collectAsState()
                    LaunchedEffect(locked) {
                        if (!locked) {
                            finish()
                            overridePendingTransition(0, 0)
                        }
                    }
                    // Back leaves the app (still locked) instead of revealing it.
                    LockScreen(onBack = { moveTaskToBack(true) })
                }
            }
        }
    }
}
