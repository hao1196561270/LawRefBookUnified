package com.lawrefbook.unified.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/** 用户库（Room）：收藏、历史、检索索引。目录库(db.sqlite3)通过框架 SQLite 只读访问。 */

@Entity(tableName = "favorites")
data class FavoritesEntity(
    @PrimaryKey val id: String,            // 通常 = "$lawId|$article"
    val lawId: String,
    val lawName: String,
    val article: String,
    val content: String,
    val classify: String = "",            // 收藏分类（借鉴 IncoderApp 的 libraries.classify）
    val tag: String = "",                  // 标签
    val createTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val lawId: String,
    val lawName: String,
    val lastRead: Long = System.currentTimeMillis(),
    // 阅读进度：LazyColumn 的首个可见项索引与像素偏移（用于“继续阅读”恢复位置）
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0
)

@Entity(tableName = "law_item")
@TypeConverters(Converters::class)
data class LawItemEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Int = 0,
    val lawId: String,
    val lawName: String,
    val article: String,
    val content: String,
    val breadcrumb: List<String>,
    val category: String,
    // 以下字段用于“按分类/标签筛选”与“按时间范围/字段排序”
    val level: String = "",        // 效力层级：法律/行政法规/部门规章/司法解释/...
    val publish: String = "",      // 发布日期 yyyy-MM-dd（来自 law.publish）
    val tags: String = "",         // 标签（来自 law.tags，逗号分隔）
    val categoryId: String = ""    // 所属分类 id（来自 law.category_id）
)
