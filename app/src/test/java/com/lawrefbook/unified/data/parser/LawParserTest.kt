package com.lawrefbook.unified.data.parser

import com.lawrefbook.unified.data.model.flatten
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析器单元测试（纯 JVM，可在 Android Studio 以 `./gradlew testDebugUnitTest` 运行，无需设备）。
 * 覆盖在调研阶段已用真实数据验证的要点：跳过 frontmatter、标题嵌套、条文拆分与续行、扁平化面包屑。
 */
class LawParserTest {

    @Test
    fun `skip frontmatter`() {
        val lines = listOf(
            "---",
            "title: 测试",
            "---",
            "第一条 内容A",
            "第二条 内容B"
        )
        val flat = LawParser.parse(lines).flatten()
        assertEquals(2, flat.size)
        assertEquals("第一条", flat[0].article)
        assertEquals("内容A", flat[0].content)
        assertEquals("第二条", flat[1].article)
    }

    @Test
    fun `nested headings build correct tree`() {
        val lines = listOf(
            "# 第一章 总则",
            "第一条 内容A",
            "## 第一节",
            "第二条 内容B",
            "# 第二章 分则",
            "第三条 内容C"
        )
        val tree = LawParser.parse(lines)
        assertEquals(2, tree.groups.size)
        val ch1 = tree.groups[0]
        assertEquals("第一章 总则", ch1.title)
        assertEquals(1, ch1.groups.size)
        assertEquals("第一节", ch1.groups[0].title)
        assertEquals("第一条", ch1.items[0].article)
        assertEquals("第二条", ch1.groups[0].items[0].article)
        assertEquals("第三条", tree.groups[1].items[0].article)
    }

    @Test
    fun `multi line article content accumulates`() {
        val lines = listOf(
            "第一条 开头",
            "续行内容",
            "第二条 另起"
        )
        val flat = LawParser.parse(lines).flatten()
        assertEquals(2, flat.size)
        assertEquals("开头\n续行内容", flat[0].content)
        assertEquals("另起", flat[1].content)
    }

    @Test
    fun `multiple continuation paragraphs keep newlines`() {
        // 多段落条文：续行之间必须以换行分隔，不能合并成一段
        val lines = listOf(
            "第一条 甲",
            "乙",
            "丙",
            "第二条 丁"
        )
        val flat = LawParser.parse(lines).flatten()
        assertEquals(2, flat.size)
        assertEquals("甲\n乙\n丙", flat[0].content)
        assertEquals("丁", flat[1].content)
    }

    @Test
    fun `flatten breadcrumb includes ancestor headings`() {
        val lines = listOf(
            "# 编",
            "## 章",
            "第一条 甲"
        )
        val flat = LawParser.parse(lines).flatten()
        assertEquals(listOf("编", "章"), flat[0].breadcrumb)
    }

    @Test
    fun `heading level computed by hash count`() {
        val lines = listOf(
            "### 三级标题",
            "第一条 内容"
        )
        val tree = LawParser.parse(lines)
        assertEquals(1, tree.groups.size)
        assertEquals(3, tree.groups[0].level)
        assertEquals("三级标题", tree.groups[0].title)
    }

    @Test
    fun `html comment markers are stripped from content`() {
        // 数据源用 <!-- INFO END --> 标记元数据区结束、<!-- FORCE BREAK --> 强制分段，
        // 两者都不应泄漏到条文正文
        val lines = listOf(
            "---",
            "title: 测试法",
            "---",
            "<!-- INFO END -->",
            "第一条 内容A",
            "<!-- FORCE BREAK -->",
            "第二条 内容B"
        )
        val flat = LawParser.parse(lines).flatten()
        assertEquals(2, flat.size)
        assertEquals("第一条", flat[0].article)
        assertEquals("内容A", flat[0].content)
        assertEquals("第二条", flat[1].article)
        assertEquals("内容B", flat[1].content)
        assertTrue(flat.none { "<!--" in it.content })
    }

    @Test
    fun `inline html comment in heading is stripped`() {
        // 模板中的行内注释（# 标题 <!-- 备注 -->）应被剥离，仅保留标题文字
        val lines = listOf(
            "# 第一章 总则 <!-- 说明 -->",
            "第一条 内容"
        )
        val tree = LawParser.parse(lines)
        assertEquals("第一章 总则", tree.groups[0].title)
    }

    @Test
    fun `comment line between article paragraphs does not merge them`() {
        // 注释行不应产生空内容条目，也不应把前后段落错误合并
        val lines = listOf(
            "第一条 甲",
            "<!-- FORCE BREAK -->",
            "乙",
            "第二条 丙"
        )
        val flat = LawParser.parse(lines).flatten()
        assertEquals(2, flat.size)
        assertEquals("甲\n乙", flat[0].content)
        assertEquals("丙", flat[1].content)
    }
}
