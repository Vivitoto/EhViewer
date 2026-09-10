package com.hippo.ehviewer.ui

import com.ehviewer.core.model.BaseGalleryInfo
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.parser.ParserUtils
import com.hippo.ehviewer.client.parser.Torrent
import io.ktor.http.encodeURLParameter

object BatchTorrentPickMode {
    const val LARGEST_SIZE = 0
    const val LATEST_TIME = 1
}

suspend fun collectBatchTorrentMagnetLinks(galleries: Collection<BaseGalleryInfo>): List<String> = withIOContext {
    val key = EhEngine.getTorrentKey()
    val pickMode = Settings.batchTorrentPickMode.value
    galleries.mapNotNull { gallery ->
        runCatching {
            EhEngine.getTorrentList(gallery.gid, gallery.token)
                .pickBatchTorrent(pickMode)
                ?.toMagnetLink(gallery.gid, key)
        }.getOrNull()
    }
}

private fun List<Torrent>.pickBatchTorrent(pickMode: Int) = when (pickMode) {
    BatchTorrentPickMode.LATEST_TIME -> maxByOrNull { runCatching { ParserUtils.parseDate(it.posted) }.getOrDefault(0L) }
    else -> maxByOrNull { it.sizeInBytes() }
}

private fun Torrent.toMagnetLink(gid: Long, key: String?): String {
    val hash = url.dropLast(8).takeLast(40)
    val encodedName = name.encodeURLParameter()
    val tracker = EhUrl.getTrackerUrl(gid, key).encodeURLParameter()
    return "magnet:?xt=urn:btih:$hash&dn=$encodedName&tr=$tracker"
}

private fun Torrent.sizeInBytes(): Double {
    val match = TorrentSizeRegex.find(size) ?: return 0.0
    val value = match.groupValues[1].toDoubleOrNull() ?: return 0.0
    return value * when (match.groupValues[2].first().uppercaseChar()) {
        'K' -> 1024.0
        'M' -> 1024.0 * 1024.0
        'G' -> 1024.0 * 1024.0 * 1024.0
        'T' -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        else -> 1.0
    }
}

private val TorrentSizeRegex = Regex("""(\d+(?:\.\d+)?)\s*([KMGT]?i?B)""", RegexOption.IGNORE_CASE)
