package com.lawrefbook.unified.data

import android.content.Context
import androidx.core.content.edit
import com.lawrefbook.unified.data.model.CategoryEntity
import com.lawrefbook.unified.data.model.LawEntity
import com.lawrefbook.unified.data.model.LawGroup
import com.lawrefbook.unified.data.model.SearchResult
import com.lawrefbook.unified.data.model.flatten
import com.lawrefbook.unified.data.parser.LawParser
import com.lawrefbook.unified.data.search.SearchMode
import com.lawrefbook.unified.data.search.SearchQuery
import com.lawrefbook.unified.data.search.SortField
import com.lawrefbook.unified.data.search.SortOrder
import com.lawrefbook.unified.data.search.articleOrdinal
import com.lawrefbook.unified.data.search.escapeLike
import com.lawrefbook.unified.data.search.levelOrder
import com.lawrefbook.unified.data.search.subsequencePattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 统一数据仓库：整合目录库（只读）与用户库（Room：收藏/历史/检索索引）。
 * 整合点：xloger 的 Room 收藏 + IncoderApp 的 raw-SQLite 目录/自定义分类 + 二者共享的 Laws 数据源。
 */
class LawRepository(private val context: Context) {

    private val catalog = AssetsLawDataSource(context)
    private val db = LawDatabase.get(context)

    init {
        catalog.ensureCatalogCopied()
    }

    // ---- 目录 ----
    suspend fun getCategories(): List<CategoryEntity> = withContext(Dispatchers.IO) {
        catalog.getCategories()
    }

    suspend fun getLaws(categoryId: String): List<LawEntity> = withContext(Dispatchers.IO) {
        catalog.getLaws(categoryId)
    }

    suspend fun getLawById(id: String): LawEntity? = withContext(Dispatchers.IO) {
        catalog.getLawById(id)
    }

    suspend fun parseLaw(law: LawEntity): LawGroup = withContext(Dispatchers.IO) {
        catalog.parseLaw(law)
    }

    // ---- 检索索引（首次启动构建一次；schema 升级时强制重建） ----
    suspend fun ensureSearchIndex() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("lawrefbook", Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_INDEX_VERSION, 0) >= INDEX_VERSION) return@withContext
        val dao = db.lawItemDao()
        dao.clear()
        for (law in catalog.getAllLaws()) {
            runCatching {
                val folder = catalog.categoryFolder(law.categoryId)
                val path = catalog.resolvePath(folder, law.name, law.fileName, law.publish, law.subTitle)
                val lines = catalog.readLawLines(path)
                val tree = LawParser.parse(lines)
                for (flat in tree.flatten()) {
                    dao.insert(
                        LawItemEntity(
                            lawId = law.id, lawName = law.name,
                            article = flat.article, content = flat.content,
                            breadcrumb = flat.breadcrumb, category = folder,
                            level = law.level, publish = law.publish ?: "",
                            tags = law.tags ?: "", categoryId = law.categoryId
                        )
                    )
                }
            }
        }
        prefs.edit { putInt(KEY_INDEX_VERSION, INDEX_VERSION) }
    }

    /** 效力层级列表（检索筛选用）。 */
    suspend fun getLevels(): List<String> = withContext(Dispatchers.IO) {
        catalog.getLevels()
    }

    /**
     * 统一检索入口：根据 query.mode 动态切换查询逻辑（精确/模糊），
     * 分类/层级/时间范围筛选在 SQL 中执行（不设 LIMIT，避免先截断后筛选漏命中），
     * 最后按指定字段或相关度在内存中排序，并截取 query.limit 条。
     */
    suspend fun search(query: SearchQuery): List<SearchResult> = withContext(Dispatchers.IO) {
        val kw = query.keyword.trim()
        val dao = db.lawItemDao()
        val rows: List<LawItemEntity> = if (kw.isBlank()) {
            dao.searchBrowse(query.categoryId, query.level, query.publishFrom, query.publishTo)
        } else when (query.mode) {
            SearchMode.EXACT ->
                dao.searchExact(escapeLike(kw), query.categoryId, query.level, query.publishFrom, query.publishTo)
            SearchMode.FUZZY ->
                dao.searchFuzzy(escapeLike(kw), subsequencePattern(kw), query.categoryId, query.level, query.publishFrom, query.publishTo)
        }
        sortResults(rows, query, kw).take(query.limit).map { it.toSearchResult() }
    }

    // ---- 收藏 ----
    suspend fun addFavorite(entity: FavoritesEntity) = withContext(Dispatchers.IO) {
        db.favoritesDao().insert(entity)
    }

    suspend fun removeFavorite(id: String) = withContext(Dispatchers.IO) {
        db.favoritesDao().deleteById(id)
    }

    suspend fun isFavorite(id: String): Boolean = withContext(Dispatchers.IO) {
        db.favoritesDao().getById(id) != null
    }

    fun favoritesFlow(): Flow<List<FavoritesEntity>> = db.favoritesDao().getAll()

    // ---- 历史 ----
    suspend fun addHistory(entity: HistoryEntity) = withContext(Dispatchers.IO) {
        db.historyDao().upsert(entity.lawId, entity.lawName, entity.lastRead)
    }

    /** 保存阅读进度（LazyColumn 首个可见项索引 + 像素偏移），供“继续阅读”恢复。 */
    suspend fun saveScrollPos(lawId: String, index: Int, offset: Int) = withContext(Dispatchers.IO) {
        db.historyDao().updateScroll(lawId, index, offset)
    }

    suspend fun getHistory(lawId: String): HistoryEntity? = withContext(Dispatchers.IO) {
        db.historyDao().getByLawId(lawId)
    }

    fun historyFlow(): Flow<List<HistoryEntity>> = db.historyDao().getAll()

    companion object {
        /** 检索索引 schema/解析版本；递增会强制清空并重建索引以补齐新字段或新解析结果。 */
        private const val INDEX_VERSION = 3
        private const val KEY_INDEX_VERSION = "search_index_version"
    }
}

/** LawItemEntity → SearchResult 便捷构造已在 SearchResult 数据类中定义；此处提供扩展 */
fun LawItemEntity.toSearchResult() = SearchResult(
    lawId = lawId, lawName = lawName, article = article,
    content = content, breadcrumb = breadcrumb, category = category,
    level = level, publish = publish
)

/**
 * 按 SearchQuery 排序：相关度模式按命中评分降序（相关度天然是“最优在前”，
 * 有意忽略 sortOrder 的升序语义）；其余字段按对应列排序并应用升降序。
 * 评分逻辑见 [relevanceScore]。
 */
private fun sortResults(
    rows: List<LawItemEntity>,
    query: SearchQuery,
    kw: String
): List<LawItemEntity> {
    if (query.sortField == SortField.RELEVANCE) {
        return rows.sortedByDescending { relevanceScore(it, kw) }
    }
    val cmp: Comparator<LawItemEntity> = when (query.sortField) {
        SortField.LAW_NAME -> compareBy { it.lawName }
        SortField.ARTICLE -> compareBy { articleOrdinal(it.article) }
        SortField.CATEGORY -> compareBy { it.category }
        SortField.LEVEL -> compareBy { levelOrder(it.level) }
        SortField.PUBLISH -> compareBy { it.publish }
        SortField.RELEVANCE -> compareBy { it.rowId }
    }
    val sorted = rows.sortedWith(cmp)
    return if (query.sortOrder == SortOrder.DESCENDING) sorted.reversed() else sorted
}

/** 相关度评分：字段精确命中 > 条号命中 > 法规名命中 > 内容命中（越靠前越高）。 */
private fun relevanceScore(item: LawItemEntity, kw: String): Int {
    if (kw.isEmpty()) return 0
    var score = 0
    if (item.article == kw || item.lawName == kw) score += 1000
    val ci = item.content.indexOf(kw)
    if (ci >= 0) score += 500 - ci.coerceAtMost(400)
    val ai = item.article.indexOf(kw)
    if (ai >= 0) score += 300
    val li = item.lawName.indexOf(kw)
    if (li >= 0) score += 200
    return score
}
