package com.lawrefbook.unified.data.search

/**
 * 统一检索规格：搜索方式（精确/模糊）、筛选条件（分类/层级/时间范围）与排序方式
 * 全部收敛到一个 SearchQuery 中，由 [com.lawrefbook.unified.data.LawRepository.search]
 * 解析并动态切换底层查询逻辑。
 *
 * 设计要点：
 *  - 模式切换：EXACT 仅接受连续、完整的片段命中（不容错）；FUZZY 在子串基础上额外
 *    支持“同序容错”（关键词字符按相同顺序出现即可，容忍遗漏/错字）。
 *  - 筛选在内存中执行（关键词查询已先把结果集收敛到很小），既高效又避免动态拼 SQL 带来的注入风险。
 *  - 排序支持相关度与多种字段（法规名/条号/分类/层级/发布时间）及升降序。
 */

/** 搜索方式 */
enum class SearchMode {
    /** 精确匹配：关键词作为完整、连续片段命中，不做容错（适合准确条号/法规名）。 */
    EXACT,
    /** 模糊匹配：部分匹配 + 同序容错（容忍遗漏与错字）。 */
    FUZZY
}

/** 排序字段 */
enum class SortField {
    RELEVANCE,  // 相关度（按命中评分）
    LAW_NAME,   // 法规名称
    ARTICLE,    // 条号（按序号）
    CATEGORY,   // 分类
    LEVEL,      // 效力层级
    PUBLISH     // 发布时间
}

/** 排序方向 */
enum class SortOrder {
    ASCENDING,
    DESCENDING
}

/**
 * 统一检索请求。
 * @param keyword   关键词（为空时仅按筛选条件浏览）
 * @param mode      搜索方式，默认 FUZZY
 * @param categoryId 按分类筛选（目录库 category.id）；null 表示不限
 * @param level     按效力层级筛选（如“法律”“行政法规”）；null 表示不限
 * @param publishFrom 发布时间下界，形如 "yyyy" 或 "yyyy-MM-dd"；null 表示不限
 * @param publishTo   发布时间上界，同 publishFrom 格式；null 表示不限
 * @param sortField 排序字段，默认 RELEVANCE
 * @param sortOrder 排序方向，默认 ASCENDING
 * @param limit     结果上限
 */
data class SearchQuery(
    val keyword: String = "",
    val mode: SearchMode = SearchMode.FUZZY,
    val categoryId: String? = null,
    val level: String? = null,
    val publishFrom: String? = null,
    val publishTo: String? = null,
    val sortField: SortField = SortField.RELEVANCE,
    val sortOrder: SortOrder = SortOrder.ASCENDING,
    val limit: Int = 500
)

/**
 * 转义 LIKE 通配符，使 % 与 _ 被当作字面量。
 * 配合 SQL 的 `ESCAPE '\'` 使用。
 */
fun escapeLike(s: String): String =
    s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

/**
 * 构造“同序容错”子序列模式：关键词每个字符之间插入 %，
 * 例如 "正当防卫" -> "%正%当%防%卫%"。配合 ESCAPE 转义单个字符中的通配符。
 */
fun subsequencePattern(kw: String): String =
    kw.map { "%" + escapeLike(it.toString()) }.joinToString("") + "%"

/** 效力层级展示顺序（用于排序与 UI 分组）。 */
private val LEVEL_ORDER = mapOf(
    "宪法" to 0,
    "法律" to 1,
    "行政法规" to 2,
    "部门规章" to 3,
    "司法解释" to 4,
    "其他" to 5,
    "案例" to 6
)

fun levelOrder(level: String): Int = LEVEL_ORDER[level] ?: Int.MAX_VALUE

/**
 * 将条号转为可比较的序号，例如 "第十条" -> 10、"第100条" -> 100。
 * 无法解析时返回 Long.MAX_VALUE（排到最后）。
 */
fun articleOrdinal(article: String): Long {
    val arabic = Regex("""第\s*(\d+)\s*条""").find(article)
    if (arabic != null) return arabic.groupValues[1].toLongOrNull() ?: Long.MAX_VALUE
    val cn = Regex("""第\s*([零〇一二两三四五六七八九十百千]+)\s*条""").find(article)
    if (cn != null) return cnToLong(cn.groupValues[1])
    return Long.MAX_VALUE
}

/** 中文数字（支持 0~9999 量级）转 Long。 */
private fun cnToLong(s: String): Long {
    if (s == "零" || s == "〇") return 0
    var section = 0L
    var number = 0L
    for (ch in s) {
        when (ch) {
            '零', '〇' -> {}
            '一' -> number = 1
            '二', '两' -> number = 2
            '三' -> number = 3
            '四' -> number = 4
            '五' -> number = 5
            '六' -> number = 6
            '七' -> number = 7
            '八' -> number = 8
            '九' -> number = 9
            '十' -> {
                if (number == 0L) number = 1
                section += number * 10
                number = 0
            }
            '百' -> {
                if (number == 0L) number = 1
                section += number * 100
                number = 0
            }
            '千' -> {
                if (number == 0L) number = 1
                section += number * 1000
                number = 0
            }
        }
    }
    return section + number
}
