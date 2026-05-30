package org.assistix.proto.nativeapp.data

import android.content.Context
import java.io.File

data class ProtoStorageBreakdown(
    val rootPath: String,
    val totalBytes: Long,
    val databaseBytes: Long,
    val cacheBytes: Long,
    val sttBytes: Long,
    val prefsBytes: Long,
    val backupsBytes: Long,
    val exportsBytes: Long,
    val avatarsBytes: Long,
)

object ProtoStorageStats {
    fun scan(context: Context): ProtoStorageBreakdown {
        val root = ProtoPersistentStorage.rootDir(context)
        val db = ProtoPersistentStorage.databaseFile(context)
        return ProtoStorageBreakdown(
            rootPath = root.absolutePath,
            totalBytes = dirSize(root),
            databaseBytes = fileSize(db),
            cacheBytes = dirSize(ProtoPersistentStorage.cacheDir(context)),
            sttBytes = dirSize(ProtoPersistentStorage.sttDir(context)),
            prefsBytes = dirSize(ProtoPersistentStorage.prefsDir(context)),
            backupsBytes = dirSize(ProtoPersistentStorage.backupsDir(context)),
            exportsBytes = dirSize(ProtoPersistentStorage.exportsDir(context)),
            avatarsBytes = dirSize(ProtoPersistentStorage.avatarsDir(context)),
        )
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var sum = 0L
        dir.walkTopDown().maxDepth(8).forEach { f ->
            if (f.isFile) sum += f.length()
        }
        return sum
    }

    private fun fileSize(f: File): Long = if (f.isFile) f.length() else 0L

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }
}
