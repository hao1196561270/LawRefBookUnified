package com.lawrefbook.unified.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 宪法修正案解析测试（纯 JVM）。
 * fixture 为真实文件：宪法修正案（2018年/2004年）.md。
 */
class AmendmentParserTest {

    private fun resource(name: String): String {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream(name)) {
            "test resource missing: $name"
        }
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `2018 amendment parses entries with article keys`() {
        val list = AmendmentParser.parse(resource("amend_2018.md"), 2018)
        assertTrue("应解析出多条修正，实际 ${list.size}", list.size >= 20)
        // 第三十二条 → 序言修正
        val prelude = list.first { it.target.contains("序言") }
        assertEquals("序言", prelude.article)
        assertTrue(prelude.detail.contains("修改为"))
        // 第三十六条 → 第一条
        assertTrue(list.any { it.article == "第一条" && it.detail.contains("中国共产党领导是中国特色社会主义最本质的特征") })
        // 第三十九条 → 第二十四条
        assertTrue(list.any { it.article == "第二十四条" })
        // 增加监察委员会一节 → 第三章
        assertTrue(list.any { it.article == "第三章" })
        // 所有条目年份一致
        assertTrue(list.all { it.year == 2018 })
    }

    @Test
    fun `2004 amendment parses entries`() {
        val list = AmendmentParser.parse(resource("amend_2004.md"), 2004)
        assertTrue("应解析出多条修正，实际 ${list.size}", list.size >= 10)
        // 第二十条 → 第十条（土地征收征用）
        val land = list.firstOrNull { it.article == "第十条" }
        assertNotNull(land)
        assertTrue(land!!.detail.contains("征收或者征用"))
        // 第二十四条 → 第三十三条（人权）
        assertTrue(list.any { it.article == "第三十三条" && it.detail.contains("国家尊重和保障人权") })
        // 第四章章名修改
        assertTrue(list.any { it.target.contains("第四章") })
    }

    @Test
    fun `article key matches for 第二十七条 增加条款`() {
        val list = AmendmentParser.parse(resource("amend_2018.md"), 2018)
        val a = list.firstOrNull { it.article == "第二十七条" }
        assertNotNull(a)
        assertTrue(a!!.detail.contains("宪法宣誓"))
    }

    @Test
    fun `criminal amendment uses 一二三 entries and extracts year from text`() {
        // 刑法修正案（四）：文件名无年份、条目为"一、二、三"，年份在正文
        val list = AmendmentParser.parse(resource("amend_criminal_4.md"), null)
        assertTrue("应解析出多条，实际 ${list.size}", list.size >= 6)
        assertEquals(2002, list.first().year)
        // 第一百四十五条：将刑法第一百四十五条修改为
        assertTrue(list.any { it.article == "第一百四十五条" && it.detail.contains("生产不符合保障人体健康") })
        // 在第一百五十二条中增加一款
        assertTrue(list.any { it.article == "第一百五十二条" })
        // 刑法第二百四十四条后增加一条
        assertTrue(list.any { it.article == "第二百四十四条" })
    }

    @Test
    fun `criminal amendment 12 with date suffix parses`() {
        val list = AmendmentParser.parse(resource("amend_criminal_12.md"), 2023)
        assertTrue("应解析出多条，实际 ${list.size}", list.size >= 5)
        assertTrue(list.all { it.year == 2023 })
    }
}
