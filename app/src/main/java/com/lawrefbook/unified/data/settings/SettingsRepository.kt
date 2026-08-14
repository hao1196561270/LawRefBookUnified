package com.lawrefbook.unified.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lawrefbook.unified.data.BuiltinData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lawrefbook_settings")

/**
 * 法条数据更新状态（运行时瞬时状态，不持久化）。
 */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object NoUpdate : UpdateStatus
    data class Available(val newCommit: String) : UpdateStatus
    data object Downloading : UpdateStatus
    data object Applying : UpdateStatus
    data object Updated : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

/**
 * 设置仓库（DataStore）。整合 IncoderApp 的设置项：深色模式、自定义主题色、
 * 正文字号、行距、法条间距，以及首页自定义分类（显隐与顺序，存为 JSON 字符串）。
 * 另含法条数据版本信息（当前数据提交、更新时间、上次检查时间、自动更新开关），
 * 以及运行时更新状态（updateStatus / updateProgress）。
 */
class SettingsRepository(private val context: Context) {

    private val ds = context.dataStore

    val darkMode: Flow<Boolean> = ds.data.map { it[KEY_DARK] ?: false }
    val dynamicColor: Flow<Boolean> = ds.data.map { it[KEY_DYNAMIC] ?: true }
    val themeSeed: Flow<Long> = ds.data.map { it[KEY_SEED] ?: DEFAULT_SEED }
    val fontSize: Flow<Float> = ds.data.map { it[KEY_FONT] ?: 17f }
    val lineSpacing: Flow<Float> = ds.data.map { it[KEY_LINE] ?: 1.5f }
    val articleSpacing: Flow<Float> = ds.data.map { it[KEY_ART] ?: 8f }
    val customCategories: Flow<String> = ds.data.map { it[KEY_CATS] ?: "" }

    // ---- 法条数据版本 ----
    val dataCommitSha: Flow<String> = ds.data.map { it[KEY_DATA_COMMIT] ?: BuiltinData.COMMIT }
    val dataUpdatedAt: Flow<Long> = ds.data.map { it[KEY_DATA_UPDATED_AT] ?: 0L }
    val lastCheckAt: Flow<Long> = ds.data.map { it[KEY_LAST_CHECK_AT] ?: 0L }
    val autoUpdate: Flow<Boolean> = ds.data.map { it[KEY_AUTO_UPDATE] ?: true }

    // ---- 运行时更新状态（不持久化） ----
    val updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateProgress = MutableStateFlow(0)

    suspend fun setDarkMode(v: Boolean) = ds.edit { it[KEY_DARK] = v }
    suspend fun setDynamicColor(v: Boolean) = ds.edit { it[KEY_DYNAMIC] = v }
    suspend fun setThemeSeed(v: Long) = ds.edit { it[KEY_SEED] = v }
    suspend fun setFontSize(v: Float) = ds.edit { it[KEY_FONT] = v }
    suspend fun setLineSpacing(v: Float) = ds.edit { it[KEY_LINE] = v }
    suspend fun setArticleSpacing(v: Float) = ds.edit { it[KEY_ART] = v }
    suspend fun setCustomCategories(v: String) = ds.edit { it[KEY_CATS] = v }

    suspend fun setDataVersion(commit: String, updatedAt: Long) =
        ds.edit { it[KEY_DATA_COMMIT] = commit; it[KEY_DATA_UPDATED_AT] = updatedAt }

    suspend fun setLastCheck(ts: Long) = ds.edit { it[KEY_LAST_CHECK_AT] = ts }
    suspend fun setAutoUpdate(v: Boolean) = ds.edit { it[KEY_AUTO_UPDATE] = v }

    fun setUpdateStatus(s: UpdateStatus) { updateStatus.value = s }
    fun setUpdateProgress(p: Int) { updateProgress.value = p.coerceIn(0, 100) }

    companion object {
        private val KEY_DARK = booleanPreferencesKey("dark_mode")
        private val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        private val KEY_SEED = longPreferencesKey("theme_seed")
        private val KEY_FONT = floatPreferencesKey("font_size")
        private val KEY_LINE = floatPreferencesKey("line_spacing")
        private val KEY_ART = floatPreferencesKey("article_spacing")
        private val KEY_CATS = stringPreferencesKey("custom_categories")
        private val KEY_DATA_COMMIT = stringPreferencesKey("data_commit")
        private val KEY_DATA_UPDATED_AT = longPreferencesKey("data_updated_at")
        private val KEY_LAST_CHECK_AT = longPreferencesKey("last_check_at")
        private val KEY_AUTO_UPDATE = booleanPreferencesKey("auto_update")
        private const val DEFAULT_SEED = 0xFF1565C0L // 默认主题色（深蓝）
    }
}
