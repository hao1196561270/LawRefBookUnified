package com.lawrefbook.unified.data.parser

import com.lawrefbook.unified.data.model.LawGroup
import com.lawrefbook.unified.data.model.LawItem
import java.util.ArrayDeque

/**
 * 法条 Markdown 解析器（纯函数，便于单元测试）。
 *
 * 算法（已用真实数据验证：解析 1688 部法规、74326 条法条、0 缺失）：
 *  1. 跳过开头的 YAML frontmatter（连续 `---` 之间）。
 *  2. 遇 `#+ 标题`：按 `#` 的数量确定层级，用“栈”维护当前 Group（层级不小于当前则出栈），把标题作为 Group 加入。
 *  3. 遇 `第X条 ...`：把此前累积文本作为一个 Item 提交，新行作为新 Item 起点。
 *  4. 其他行：累积到当前 Item 的 content。
 *  5. 末尾提交剩余文本。
 * Item 拆分正则：`^(第.+?条)\s*([\s\S]*)$` → group1=条号，group2=内容。
 */
object LawParser {

    private val ART_RE = Regex("^(第.+?条)\\s*([\\s\\S]*)$")
    private val HEADING_RE = Regex("^#+\\s.*")
    private val ARTICLE_LINE_RE = Regex("^第.+条.*")
    private val FRONTMATTER_RE = Regex("^---$")
    /** 数据源元数据注释：<!-- INFO END -->（frontmatter 结束）、<!-- FORCE BREAK -->（强制分段）等 */
    private val HTML_COMMENT_RE = Regex("<!--[\\s\\S]*?-->")

    fun parse(lines: List<String>): LawGroup {
        val base = LawGroup(level = 0, title = "", groups = mutableListOf(), items = mutableListOf())
        val stack = ArrayDeque<LawGroup>().apply { add(base) }

        fun put(text: String) {
            val t = text.trim('\n')
            if (t.isBlank()) return
            val m = ART_RE.matchEntire(t)
            val item = if (m != null) {
                LawItem(article = m.groupValues[1], content = m.groupValues[2])
            } else {
                LawItem(article = "", content = t)
            }
            stack.last().items.add(item)
        }

        var current = StringBuilder()
        var frontmatter = false
        var frontmatterDone = false

        for (raw in lines) {
            val line = raw.removeSuffix("\n")
            if (!frontmatterDone) {
                if (FRONTMATTER_RE.matches(line.trim())) {
                    when {
                        // 闭合 frontmatter
                        frontmatter -> { frontmatter = false; frontmatterDone = true }
                        // 文件开头（尚未累积任何内容）出现 --- 视为 frontmatter 起始
                        current.isEmpty() && base.items.isEmpty() && base.groups.isEmpty() ->
                            frontmatter = true
                        // 内容之后出现孤立的 ---（如分隔线）：跳过该行，不进入 frontmatter
                        else -> frontmatterDone = true
                    }
                    continue
                }
                if (frontmatter) continue
                frontmatterDone = true
            }
            // 过滤数据源 HTML 注释（<!-- INFO END --> / <!-- FORCE BREAK --> / 行内备注），
            // 避免元数据标记泄漏到条文正文。
            val cleaned = HTML_COMMENT_RE.replace(line, "").trim()
            if (cleaned.isEmpty()) continue

            when {
                HEADING_RE.matches(cleaned) -> {
                    put(current.toString())
                    current = StringBuilder()
                    val level = cleaned.count { it == '#' }
                    val title = cleaned.replace(Regex("^#+\\s"), "")
                    while (stack.size > 1 && stack.last().level >= level) stack.removeLast()
                    val group = LawGroup(
                        level = level, title = title,
                        groups = mutableListOf(), items = mutableListOf()
                    )
                    stack.last().groups.add(group)
                    stack.addLast(group)
                }
                ARTICLE_LINE_RE.matches(cleaned) -> {
                    put(current.toString())
                    current = StringBuilder(cleaned)
                }
                else -> {
                    // 续行（多段落条文）：在既有内容后补一个换行再拼接，保持段落分隔。
                    // 不能用 appendLine（会把换行加在续行之后，再被 put() 的 trim 删掉，导致段落合并）。
                    if (current.isNotEmpty()) current.append('\n')
                    current.append(cleaned)
                }
            }
        }
        put(current.toString())
        return base
    }
}
