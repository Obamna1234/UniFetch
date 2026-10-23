package com.unifetch.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.unifetch.app.App.Companion.context
import com.unifetch.app.ui.common.LocalDarkTheme
import com.unifetch.app.ui.common.SettingsProvider
import com.unifetch.app.ui.page.AppEntry
import com.unifetch.app.ui.page.lock.AppLockGate
import com.unifetch.app.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.unifetch.app.ui.theme.SealTheme
import com.unifetch.app.util.PreferenceUtil
import com.unifetch.app.util.matchAllUrlsFromSharedText
import com.unifetch.app.util.setLanguage
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext

class MainActivity : AppCompatActivity() {
    private val dialogViewModel: DownloadDialogViewModel by viewModel()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < 33) {
            runBlocking { setLanguage(PreferenceUtil.getLocaleFromPreference()) }
        }
        enableEdgeToEdge()

        context = this.baseContext
        // A share that opened UniFetcher fresh (not just onNewIntent while it was already open).
        if (savedInstanceState == null) handleSharedLinks(intent)
        setContent {
            KoinContext {
                val windowSizeClass = calculateWindowSizeClass(this)
                SettingsProvider(windowWidthSizeClass = windowSizeClass.widthSizeClass) {
                    SealTheme(
                        darkTheme = LocalDarkTheme.current.isDarkTheme(),
                        isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                    ) {
                        AppLockGate(onBackWhileLocked = { moveTaskToBack(true) }) {
                            AppEntry(dialogViewModel = dialogViewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedLinks(intent)
    }

    /** Opens the download sheet with every link that was shared to UniFetcher. */
    private fun handleSharedLinks(intent: Intent?) {
        val urls = intent?.getSharedURLs().orEmpty()
        if (urls.isNotEmpty()) {
            dialogViewModel.postAction(DownloadDialogViewModel.Action.ShowSheet(urls))
        }
    }

    private fun Intent.getSharedURLs(): List<String> {
        val intent = this

        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                listOfNotNull(intent.dataString)
            }

            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedContent ->
                    intent.removeExtra(Intent.EXTRA_TEXT)
                    matchAllUrlsFromSharedText(sharedContent)
                } ?: emptyList()
            }

            else -> {
                emptyList()
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
