package com.unifetch.app.util

import android.content.Context
import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import com.unifetch.app.App
import com.unifetch.app.R
import com.unifetch.app.util.FileUtil.getDriveWorkDir
import com.unifetch.app.util.FileUtil.getSdcardTempDir
import com.unifetch.app.util.PreferenceUtil.getBoolean
import com.unifetch.app.util.PreferenceUtil.getLong
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pauses the download queue when the storage downloads are written to is nearly full, instead
 * of letting every remaining download fail. Downloads already running carry on; nothing new
 * starts until space is freed (or the limit is lowered in settings).
 */
object StorageGuard {
    private const val TAG = "StorageGuard"

    private const val GB = 1024L * 1024 * 1024

    /** Choices for "Pause downloads when free space is below". 0 means never pause. */
    val LIMIT_OPTIONS = listOf(2 * GB, GB, GB / 2, 0L)

    private val mutablePaused = MutableStateFlow(false)

    /** True while the queue is paused because storage is almost full. */
    val paused: StateFlow<Boolean> = mutablePaused.asStateFlow()

    private var lastFree: Long = 0L
    private var lastOnDrive: Boolean = false

    fun minFreeBytes(): Long = STORAGE_MIN_FREE.getLong()

    /** Where downloads are written while downloading. */
    enum class Location {
        /** The phone's own storage (the drive option is off). */
        PHONE,
        /** Straight onto the SD card or USB drive. */
        DRIVE,
        /** The drive option is on, but this phone doesn't allow writing straight to the drive. */
        PHONE_THEN_DRIVE,
    }

    fun location(context: Context = App.context): Location =
        when {
            !SDCARD_DOWNLOAD.getBoolean() -> Location.PHONE
            context.getDriveWorkDir() != null -> Location.DRIVE
            else -> Location.PHONE_THEN_DRIVE
        }

    private fun workFolder(context: Context = App.context): File =
        if (SDCARD_DOWNLOAD.getBoolean()) context.getSdcardTempDir(null)
        else File(App.videoDownloadDir)

    /** Free space in [folder] (or the nearest folder above it that exists), or null if unknown. */
    fun freeBytesAt(folder: File?): Long? =
        runCatching {
                var f = folder
                while (f != null && !f.exists()) f = f.parentFile
                (f ?: Environment.getExternalStorageDirectory()).usableSpace
            }
            .onFailure { Log.w(TAG, "couldn't read free space", it) }
            .getOrNull()
            // 0 usually means the size couldn't be read, not that the storage is completely full.
            ?.takeIf { it > 0L }

    /** Free space where downloads are written, or null if it can't be read. */
    fun freeBytes(): Long? = freeBytesAt(workFolder())

    /** Free space on the phone's own storage. */
    fun phoneFreeBytes(): Long? = freeBytesAt(Environment.getExternalStorageDirectory())

    /**
     * True if a new download may start. Updates [paused] and the "storage almost full"
     * notification when the answer changes.
     */
    fun canStartDownload(): Boolean {
        val limit = minFreeBytes()
        val free = if (limit > 0L) freeBytes() else null
        val low = limit > 0L && free != null && free < limit
        if (low) {
            lastFree = free ?: 0L
            lastOnDrive = location() == Location.DRIVE
        }
        if (low != mutablePaused.value) {
            mutablePaused.value = low
            if (low) {
                Log.i(TAG, "storage almost full ($free bytes free): pausing the queue")
                NotificationUtil.notifyStorageLow()
            } else {
                Log.i(TAG, "storage freed or limit lowered: resuming the queue")
                NotificationUtil.cancelStorageLowNotification()
            }
        }
        return !low
    }

    fun formatSize(context: Context, bytes: Long?): String =
        if (bytes == null) context.getString(R.string.unknown)
        else Formatter.formatShortFileSize(context, bytes)

    /** e.g. "Only 1.2 GB free on the phone. …" */
    fun lowMessage(context: Context): String =
        context.getString(
            R.string.storage_low_text,
            formatSize(context, lastFree),
            context.getString(
                if (lastOnDrive) R.string.storage_location_drive
                else R.string.storage_location_phone
            ),
            limitText(context, minFreeBytes()),
        )

    fun limitText(context: Context, limit: Long): String =
        if (limit <= 0L) context.getString(R.string.storage_limit_off)
        else formatSize(context, limit)

    /** Explains where downloads are written right now, with free space. */
    fun statusText(context: Context): String =
        when (location(context)) {
            Location.PHONE ->
                context.getString(R.string.storage_status_phone, formatSize(context, phoneFreeBytes()))
            Location.DRIVE ->
                context.getString(R.string.storage_status_drive, formatSize(context, freeBytes()))
            Location.PHONE_THEN_DRIVE ->
                context.getString(
                    R.string.storage_status_phone_then_drive,
                    formatSize(context, phoneFreeBytes()),
                )
        }
}
