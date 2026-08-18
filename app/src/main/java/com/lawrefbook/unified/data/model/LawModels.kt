package com.lawrefbook.unified.data.model

/**
 * 领域模型。解析后的法条是一棵可无限嵌套的 Group 树，叶子是 Item（条）。
 * 该结构直接对应已验证的解析算法（见 data/parser/LawParser.kt）。
 */

data class LawItem(
    val article: String,   // 条号，如 "第一条"
    val content: String    // 条文内容
)

data class LawGroup(
    val level: Int,
    val title: String,
    val groups: MutableList<LawGroup>,
    val items: MutableList<LawItem>
)

/** 扁平化后的单条法条，用于检索与展示 */
data class FlatItem(
    val breadcrumb: List<String>,
    val article: String,
    val content: String
)

fun LawGroup.flatten(breadcrumb: List<String> = emptyList()): List<FlatItem> {
    val out = mutableListOf<FlatItem>()
    for (it in items) out.add(FlatItem(breadcrumb, it.article, it.content))
    for (g in groups) out += g.flatten(breadcrumb + g.title)
    return out
}

/** 法规标题：取第一层的组标题 */
val LawGroup.titleOrEmpty: String get() = groups.firstOrNull()?.title ?: ""

/**
 * 目录库中的“分类”实体（来自 db.sqlite3 的 category 表，非 Room 实体）。
 */
data class CategoryEntity(
    val id: String,
    val name: String,
    val folder: String,
    val group: String? = null,
    val isSubFolder: Int = 0,
    val order: Int? = null
)

/**
 * 目录库中的“法规”实体（来自 db.sqlite3 的 law 表，非 Room 实体）。
 */
data class LawEntity(
    val id: String,
    val level: String,
    val name: String,
    val fileName: String? = null,
    val publish: String? = null,
    val order: Int? = null,
    val subTitle: String? = null,
    val tags: String? = null,
    val categoryId: String,
    /** 效力截止日期（'2099-12-31' = 现行有效） */
    val validTo: String? = null
)

/** 法规年份：名称里的（YYYY年）优先，否则取 publish 年份；都没有 → null（现行/无年份） */
fun LawEntity.lawYear(): Int? {
    Regex("（(\\d{4})年）").find(name)?.let { return it.groupValues[1].toIntOrNull() }
    return publish?.take(4)?.toIntOrNull()
}

/** 按时间排序：无年份的现行版本排最前，其余按年份降序（最新在前），同年按名称。 */
fun List<LawEntity>.sortedByTime(): List<LawEntity> = sortedWith(
    compareBy<LawEntity> { it.lawYear() != null }      // 现行（无年份）优先
        .thenByDescending { it.lawYear() ?: 0 }        // 年份降序
        .thenBy { it.name }                            // 同年按名称
)

/** 显示名：名称未带年份时补「（年份）」，如"法律援助法" → "法律援助法（2021）" */
fun LawEntity.displayName(): String {
    if (Regex("（\\d{4}年）").containsMatchIn(name)) return name
    val year = publish?.take(4)
    return if (!year.isNullOrBlank()) "$name（$year）" else name
}

/**
 * 检索结果
 */
data class SearchResult(
    val lawId: String,
    val lawName: String,
    val article: String,
    val content: String,
    val breadcrumb: List<String>,
    val category: String,
    val level: String = "",
    val publish: String = ""
)
