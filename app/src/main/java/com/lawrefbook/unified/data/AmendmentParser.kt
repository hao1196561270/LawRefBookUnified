package com.lawrefbook.unified.data

/**
 * 宪法修正案条目：某一年对某一位置（条文/序言/章节）的修改说明。
 */
data class Amendment(
    val year: Int,              // 修正年份（如 2018）
    val article: String?,       // 匹配正文的键："第一条" / "序言" / "第三章"；无法归一化时为 null
    val target: String,         // 修正案原文的位置描述（如 "宪法序言第七自然段"）
    val detail: String          // 修正内容原文（去掉"第X条 "编号前缀）
)

/**
 * 解析宪法修正案 Markdown（如 宪法修正案（2018年）.md），
 * 提取结构化的修正条目供阅读页标注。
 *
 * 数据格式（已用真实文件验证）：
 *   第X条 宪法序言第七自然段中"…"修改为"…"；…。（或：增加一款/增写一句/删除）
 * 位置归一化：序言第N自然段 → "序言"；第N条 → "第N条"；第N章 → "第N章"。
 */
object AmendmentParser {

    private val CN_NUM = "一二三四五六七八九十百千零两"

    /** 条目起点：中文序号"一、"（刑法式）或"第X条 "（宪法式） */
    private val ENTRY_RE = Regex("(?:第[$CN_NUM]+条[\\s　]|^[$CN_NUM]+、)")
    /** 位置：宪法序言第N自然段 / (宪法|刑法)?第N条(第N款)? / 第N章 / 第四章章名 */
    private val POS_RE = Regex(
        "(?:宪法|刑法)?(?:第[$CN_NUM]+章[“\"']?[^，。；\"'）)]*[”\"']?|序言第[$CN_NUM]+自然段|第[$CN_NUM]+条(?:第[$CN_NUM]+款)?|第四章章名|第三章[“\"'][^”\"']*[”\"'])"
    )
    private val ARTICLE_RE = Regex("第[$CN_NUM]+条")
    private val CHAPTER_RE = Regex("第[$CN_NUM]+章")
    /** 正文元信息区年份（文件名无年份时回退）：YYYY年M月D日 ... 通过/修正 */
    private val YEAR_RE = Regex("(\\d{4})年\\d{1,2}月\\d{1,2}日[^\\n]*?(?:通过|修正|施行)")

    /**
     * 解析整篇修正案文本。[year] 为修正年份；为 null 时从正文元信息区提取。
     */
    fun parse(text: String, year: Int?): List<Amendment> {
        // 跳过标题/元信息区（# 开头、日期行、<!-- INFO END -->）
        val body = text.lineSequence()
            .dropWhile { line ->
                val t = line.trim()
                t.startsWith("#") || t.startsWith("<!--") || t.isEmpty() ||
                    Regex("^\\d{4}年").containsMatchIn(t)
            }
            .joinToString("\n")
        val resolvedYear = year ?: YEAR_RE.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        // 按条目起点切分（"第X条 " 或 "一、"）
        val entries = mutableListOf<String>()
        var current = StringBuilder()
        for (line in body.lineSequence()) {
            if (ENTRY_RE.containsMatchIn(line) && current.isNotBlank()) {
                entries.add(current.toString())
                current = StringBuilder()
            }
            current.append(line).append('\n')
        }
        if (current.isNotBlank()) entries.add(current.toString())

        return entries.mapNotNull { entry ->
            val text0 = entry.trim()
            // 先剥离条目编号（"第X条 " / "一、"），避免编号被当作被修正位置
            val stripped = ENTRY_RE.replaceFirst(text0, "").trim()
            val pos = POS_RE.find(stripped)?.value ?: return@mapNotNull null
            val article = when {
                pos.contains("序言") -> "序言"
                CHAPTER_RE.containsMatchIn(pos) -> CHAPTER_RE.find(pos)?.value
                ARTICLE_RE.containsMatchIn(pos) -> ARTICLE_RE.find(pos)?.value
                else -> null
            }
            Amendment(
                year = resolvedYear,
                article = article,
                target = pos,
                detail = stripped
            )
        }
    }
}
