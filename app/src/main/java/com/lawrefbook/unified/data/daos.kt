package com.lawrefbook.unified.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoritesEntity)

    @Delete
    suspend fun delete(entity: FavoritesEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM favorites ORDER BY createTime DESC")
    fun getAll(): Flow<List<FavoritesEntity>>

    @Query("SELECT * FROM favorites WHERE id = :id")
    suspend fun getById(id: String): FavoritesEntity?
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY lastRead DESC LIMIT 100")
    fun getAll(): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface LawItemDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: LawItemEntity)

    @Query("DELETE FROM law_item")
    suspend fun clear()

    /**
     * 精确匹配：关键词作为完整、连续片段命中（content/article/lawName 中连续出现，
     * 不做容错）。kw 已由调用方转义（escapeLike）。ESCAPE '\' 保证 %/_ 被当作字面量。
     *
     * 筛选条件（分类/层级/发布时间范围）直接在 SQL 中执行，不设 LIMIT——
     * 先按 rowId 截断再在内存中筛选/排序会导致漏命中（见 LawRepository.search）。
     */
    @Query(
        "SELECT * FROM law_item " +
        "WHERE (content LIKE '%' || :kw || '%' ESCAPE '\\' " +
        "   OR article LIKE '%' || :kw || '%' ESCAPE '\\' " +
        "   OR lawName LIKE '%' || :kw || '%' ESCAPE '\\') " +
        "AND (:categoryId IS NULL OR categoryId = :categoryId) " +
        "AND (:level IS NULL OR level = :level) " +
        "AND (:publishFrom IS NULL OR publish >= :publishFrom) " +
        "AND (:publishTo IS NULL OR publish <= :publishTo) " +
        "ORDER BY rowId"
    )
    suspend fun searchExact(
        kw: String,
        categoryId: String?,
        level: String?,
        publishFrom: String?,
        publishTo: String?
    ): List<LawItemEntity>

    /**
     * 模糊匹配：在精确（连续子串）基础上，额外支持“同序容错”——关键词各字符按相同
     * 顺序出现即可命中（容忍遗漏/错字）。seq 为子序列模式（已由调用方构造，如 %正%当%防%卫%）。
     * 筛选与精确版一致，同样不设 SQL LIMIT。
     */
    @Query(
        "SELECT * FROM law_item " +
        "WHERE (content LIKE '%' || :kw || '%' ESCAPE '\\' " +
        "   OR article LIKE '%' || :kw || '%' ESCAPE '\\' " +
        "   OR lawName LIKE '%' || :kw || '%' ESCAPE '\\' " +
        "   OR content LIKE :seq ESCAPE '\\' " +
        "   OR article LIKE :seq ESCAPE '\\' " +
        "   OR lawName LIKE :seq ESCAPE '\\') " +
        "AND (:categoryId IS NULL OR categoryId = :categoryId) " +
        "AND (:level IS NULL OR level = :level) " +
        "AND (:publishFrom IS NULL OR publish >= :publishFrom) " +
        "AND (:publishTo IS NULL OR publish <= :publishTo) " +
        "ORDER BY rowId"
    )
    suspend fun searchFuzzy(
        kw: String,
        seq: String,
        categoryId: String?,
        level: String?,
        publishFrom: String?,
        publishTo: String?
    ): List<LawItemEntity>

    /** 无关键词时按筛选条件浏览（分类/层级/时间由 SQL 执行，不设 LIMIT）。 */
    @Query(
        "SELECT * FROM law_item " +
        "WHERE (:categoryId IS NULL OR categoryId = :categoryId) " +
        "AND (:level IS NULL OR level = :level) " +
        "AND (:publishFrom IS NULL OR publish >= :publishFrom) " +
        "AND (:publishTo IS NULL OR publish <= :publishTo) " +
        "ORDER BY rowId"
    )
    suspend fun searchBrowse(
        categoryId: String?,
        level: String?,
        publishFrom: String?,
        publishTo: String?
    ): List<LawItemEntity>
}
