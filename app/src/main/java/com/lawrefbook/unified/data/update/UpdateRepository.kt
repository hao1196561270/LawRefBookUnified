package com.lawrefbook.unified.data.update

import android.content.Context
import android.util.Log
import com.lawrefbook.unified.data.BuiltinData
import com.lawrefbook.unified.data.LawDataManager
import com.lawrefbook.unified.data.settings.SettingsRepository
import com.lawrefbook.unified.data.settings.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 法条数据更新：每月（由 MonthlyUpdateWorker 触发）或用户手动检查，
 * 比对上游 LawRefBook/Laws 最新提交与本地数据版本；若不同则下载并同步最新内容。
 */
class UpdateRepository(
    private val context: Context,
    private val settings: SettingsRepository
) {
    /** 查询上游最新 commit SHA。 */
    suspend fun fetchLatestCommit(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(BuiltinData.REPO_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "FatiaoTong/1.0")
            }
            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("HTTP ${conn.responseCode}"))
            }
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val sha = JSONArray(text).getJSONObject(0).getString("sha")
            Result.success(sha)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 下载上游 master.zip 到临时文件（带进度），解压覆盖到 laws/ 目录。 */
    suspend fun downloadAndApply(onProgress: (Int) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(BuiltinData.REPO_ZIP).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 0
                setRequestProperty("User-Agent", "FatiaoTong/1.0")
            }
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            val tmpZip = File(context.filesDir, "laws_download.zip")
            val bis = BufferedInputStream(conn.inputStream)
            val fos = FileOutputStream(tmpZip)
            val buf = ByteArray(8192)
            var read = 0L
            var len: Int
            while (bis.read(buf).also { len = it } > 0) {
                fos.write(buf, 0, len)
                read += len
                onProgress(((read * 100) / total).toInt().coerceIn(0, 100))
            }
            fos.close(); bis.close(); conn.disconnect()

            val tmpDir = File(context.filesDir, "laws_tmp")
            tmpDir.deleteRecursively(); tmpDir.mkdirs()
            tmpZip.inputStream().use { LawDataManager.extractZipStream(it, tmpDir) }
            tmpZip.delete()

            // GitHub 归档 zip 顶层恒带 "<仓库名>-<分支>/" 前缀（如 Laws-master/），
            // 而内置 laws.zip 是根级布局（db.sqlite3 在根）。解压后若无根级 db.sqlite3，
            // 说明被前缀目录包了一层：把该目录内容上移，保证与 App 期望的布局一致。
            liftSingleRootDir(tmpDir)

            val finalDir = LawDataManager.lawsDir(context)
            finalDir.deleteRecursively()
            tmpDir.renameTo(finalDir)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UpdateRepo", "download failed", e)
            Result.failure(e)
        }
    }

    /**
     * 若 [dir] 下只有一个子目录且根下没有 db.sqlite3（GitHub 归档 zip 的
     * "Laws-master/" 前缀布局），则把该子目录内容上移一层。
     */
    private fun liftSingleRootDir(dir: File) {
        val rootFiles = dir.listFiles() ?: return
        if (rootFiles.any { it.name == "db.sqlite3" }) return
        val singleDir = rootFiles.singleOrNull { it.isDirectory } ?: return
        singleDir.listFiles()?.forEach { f ->
            val target = File(dir, f.name)
            if (!f.renameTo(target)) {
                if (f.isDirectory) {
                    f.copyRecursively(target, overwrite = true)
                    f.deleteRecursively()
                } else {
                    f.copyTo(target, overwrite = true)
                    f.delete()
                }
            }
        }
        singleDir.delete()
    }

    /**
     * 检查上游是否有新版本；若有且允许自动下载，则下载并同步最新内容。
     * 返回最终状态（供 Worker / UI 使用）。
     */
    suspend fun checkAndApplyIfNeeded(autoDownload: Boolean = true): UpdateStatus {
        settings.setUpdateStatus(UpdateStatus.Checking)
        settings.setLastCheck(System.currentTimeMillis())
        val remote = fetchLatestCommit().getOrElse {
            val s = UpdateStatus.Error(it.message ?: "网络错误")
            settings.setUpdateStatus(s); return s
        }
        val local = settings.dataCommitSha.first()
        return if (remote == local) {
            val s = UpdateStatus.NoUpdate
            settings.setUpdateStatus(s); s
        } else if (autoDownload) {
            settings.setUpdateStatus(UpdateStatus.Downloading)
            settings.setUpdateProgress(0)
            val r = downloadAndApply { p -> settings.setUpdateProgress(p) }
            if (r.isSuccess) {
                settings.setUpdateStatus(UpdateStatus.Applying)
                settings.setDataVersion(remote, System.currentTimeMillis())
                settings.setUpdateStatus(UpdateStatus.Updated)
                UpdateStatus.Updated
            } else {
                val s = UpdateStatus.Error(r.exceptionOrNull()?.message ?: "下载失败")
                settings.setUpdateStatus(s); s
            }
        } else {
            val s = UpdateStatus.Available(remote)
            settings.setUpdateStatus(s); s
        }
    }
}
