package com.lawrefbook.unified.data

import com.lawrefbook.unified.data.search.articleOrdinal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 条号排序语义测试（纯 JVM）。
 * 回归锁定中文数字转换（cnToLong）的正确性——修复前「二」「两」被误映射为 1，
 * 导致 第二十条=10、第二百三十四条=134 等排序错误。
 */
class ArticleOrdinalTest {

    @Test
    fun `single digits`() {
        assertEquals(1, articleOrdinal("第一条"))
        assertEquals(2, articleOrdinal("第二条"))
        assertEquals(9, articleOrdinal("第九条"))
    }

    @Test
    fun `tens`() {
        assertEquals(10, articleOrdinal("第十条"))
        assertEquals(11, articleOrdinal("第十一条"))
        assertEquals(20, articleOrdinal("第二十条"))
        assertEquals(21, articleOrdinal("第二十一条"))
        assertEquals(99, articleOrdinal("第九十九条"))
    }

    @Test
    fun `hundreds`() {
        assertEquals(100, articleOrdinal("第一百条"))
        assertEquals(234, articleOrdinal("第二百三十四条"))
        assertEquals(200, articleOrdinal("第二百条"))
    }

    @Test
    fun `arabic numerals`() {
        assertEquals(234, articleOrdinal("第234条"))
        assertEquals(20, articleOrdinal("第 20 条"))
    }

    @Test
    fun `unparseable falls back to max`() {
        assertEquals(Long.MAX_VALUE, articleOrdinal("附则"))
        assertEquals(Long.MAX_VALUE, articleOrdinal(""))
    }
}
