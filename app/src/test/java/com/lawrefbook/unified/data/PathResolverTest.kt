package com.lawrefbook.unified.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 路径解析候选顺序测试（纯 JVM）。
 * 该顺序在调研阶段以真实数据源验证：1688/1688 命中。
 */
class PathResolverTest {

    @Test
    fun `刑法 special case prioritizes folder-name`() {
        val c = buildCandidatePaths("刑法", "刑法", null, null, null)
        assertEquals(listOf("刑法/刑法.md", "刑法/刑法.md"), c)
    }

    @Test
    fun `ordering with subTitle fileName and publish`() {
        // 真实数据里 law.filename 不带 .md 扩展名（如 民法典 拆分出的“合同编”），
        // 代码统一追加 .md，因此这里传 “宪法” 而非 “宪法.md”。
        val c = buildCandidatePaths(
            folder = "法律",
            name = "中华人民共和国宪法",
            fileName = "宪法",
            publish = "2018",
            subTitle = "宪法（2018修正）"
        )
        assertEquals(
            listOf(
                "法律/宪法（2018修正）.md",
                "法律/宪法.md",
                "法律/中华人民共和国宪法(2018).md",
                "法律/中华人民共和国宪法.md"
            ),
            c
        )
    }

    @Test
    fun `publish variant only added when publish present`() {
        val c = buildCandidatePaths("法律", "合同法", null, null, null)
        assertEquals(listOf("法律/合同法.md"), c)
    }
}
