package com.lawrefbook.unified.data

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 法条数据（db.sqlite3 + 全部 .md）以单个 laws.zip 随 APK 发布；
 * 首次启动解压到应用私有目录 filesDir/laws/，之后所有读取都从该目录进行。
 * 月度更新下载的新 zip 也解压到该目录覆盖，实现“内置即离线、可增量同步最新”。
 */
object LawDataManager {
    private const val TAG = "LawDataManager"

    /** 记录已解压的内置数据版本（BuiltinData.COMMIT），App 升级携带新 laws.zip 时据此重新解压。 */
    private const val KEY_EXTRACTED_COMMIT = "laws_extracted_commit"

    fun lawsDir(context: Context): File = File(context.filesDir, "laws")

    /**
     * 首次启动把 assets/laws.zip 解压到 filesDir/laws/（幂等）。
     * 若目录库已存在但解压版本与内置数据版本不一致（App 升级携带新 zip），则重新解压覆盖。
     */
    fun ensureExtracted(context: Context) {
        val outDir = lawsDir(context)
        val dbFile = File(outDir, "db.sqlite3")
        val prefs = context.getSharedPreferences("lawrefbook", Context.MODE_PRIVATE)
        val extractedCommit = prefs.getString(KEY_EXTRACTED_COMMIT, null)
        if (dbFile.exists() && extractedCommit == BuiltinData.COMMIT) return
        if (dbFile.exists()) {
            Log.i(TAG, "内置数据版本变化，重新解压 laws.zip")
            outDir.deleteRecursively()
        }
        Log.i(TAG, "解压 laws.zip -> ${outDir.absolutePath}")
        context.assets.open("laws.zip").use { input ->
            extractZipStream(input, outDir)
        }
        prefs.edit().putString(KEY_EXTRACTED_COMMIT, BuiltinData.COMMIT).apply()
        Log.i(TAG, "解压完成")
    }

    /** 把 zip 输入流解压到目标目录；onBytes 报告累计读取字节数（用于进度） */
    fun extractZipStream(
        input: InputStream,
        targetDir: File,
        onBytes: (Long) -> Unit = {}
    ) {
        targetDir.mkdirs()
        val buffer = ByteArray(8192)
        ZipInputStream(BufferedInputStream(input)).use { zis ->
            var entry = zis.nextEntry
            var total = 0L
            while (entry != null) {
                val target = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                            total += len
                            onBytes(total)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
