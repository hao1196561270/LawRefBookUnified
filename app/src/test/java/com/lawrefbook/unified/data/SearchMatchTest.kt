package com.lawrefbook.unified.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 检索语义测试（纯 JVM）。
 * 整合版放弃 FTS5 trigram（旧版 Android 不支持，且 ≤2 个汉字中文会返回 0 命中），
 * 改用 SQL `LIKE '%' || :kw || '%'`（即子串匹配）。本测试锁定该语义：
 *   - 中文任意长度（含 2 字）均正确命中
 *   - 区分有无该子串
 * 与 LawItemDao.search 中的 WHERE 子句语义一致。
 */
class SearchMatchTest {

    private fun like(content: String, kw: String): Boolean =
        kw.isNotBlank() && content.contains(kw)

    @Test
    fun `two char chinese keyword matches`() {
        assertTrue(like("当事人应当按照约定履行合同义务。", "合同"))
        assertTrue(like("本法所称赔偿，包括实际损失。", "赔偿"))
    }

    @Test
    fun `keyword absent returns false`() {
        assertFalse(like("中华人民共和国的一切权力属于人民。", "合同"))
    }

    @Test
    fun `article field also searchable`() {
        assertTrue(like("第二百三十四条", "二百三十四"))
    }
}
