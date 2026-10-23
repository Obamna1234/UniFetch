package com.unifetch.app.util

import android.os.Environment
import android.util.Log
import com.unifetch.app.App
import com.unifetch.app.util.FileUtil.getSdcardTempDir
import com.unifetch.app.util.PreferenceUtil.getBoolean
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pauses the download queue when the phone is nearly full, instead of letting every remaining
 * download fail. Downloads already running carry on; nothing new starts until space is freed.
 */
object StorageGuard {
    private const val TAG = "StorageGuard"

    /** Don't start a new download with less free space than this. */
    const val MIN_FREE_BYTES: Long = 2L * 1024 * 1024 * 1024

    private val mutablePaused = MutableStateFlow(false)

    /** True while the queue is paused because storage is almost full. */
    val paused: StateFlow<Boolean> = mutablePaused.asStateFlow()

    /**
     * The folder downloads are written to while downloading: the SD card / USB drive (when that
     * option is on and Android makes the drive available) or the phone's download folder.
     */
    private fun downloadWorkFolder(): File =
        if (SDCARD_DOWNLOAD.getBoolean()) App.context.getSdcardTempDir(null)
        else File(App.videoDownloadDir)

    /** Free space where downloads are written, or null if it can't be read. */
    fun freeBytes(): Long? =
        runCatching {
                // The folder may not exist yet: use the nearest folder that does.
                var folder: File? = downloadWorkFolder()
                while (folder != null && !folder.exists()) folder = folder.parentFile
                (folder ?: Environment.getExternalStorageDirectory()).usableSpace
            }
            .onFailure { Log.w(TAG, "couldn't read free space", it) }
            .getOrNull()
            // 0 usually means the size couldn't be read, not that the phone is completely full.
            ?.takeIf { it > 0L }

    /**
     * True if a new download may start. Updates [paused] and the "storage almost full"
     * notification when the answer changes.
     */
    fun canStartDownload(): Boolean {
        val free = freeBytes()
        val low = free != null && free < MIN_FREE_BYTES
        if (low != mutablePaused.value) {
            mutablePaused.value = low
            if (low) {
                Log.i(TAG, "storage almost full ($free bytes free): pausing the queue")
                NotificationUtil.notifyStorageLow()
            } else {
                Log.i(TAG, "storage freed: resuming the queue")
                NotificationUtil.cancelStorageLowNotification()
            }
        }
        return !low
    }
}
