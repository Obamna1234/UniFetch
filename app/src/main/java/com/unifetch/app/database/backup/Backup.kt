package com.unifetch.app.database.backup

import com.unifetch.app.database.objects.CommandTemplate
import com.unifetch.app.database.objects.DownloadedVideoInfo
import com.unifetch.app.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null,
)
