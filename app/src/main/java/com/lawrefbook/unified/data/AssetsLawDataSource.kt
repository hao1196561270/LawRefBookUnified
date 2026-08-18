package com.lawrefbook.unified.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.lawrefbook.unified.data.model.CategoryEntity
import com.lawrefbook.unified.data.model.LawEntity
import com.lawrefbook.unified.data.model.LawGroup
import com.lawrefbook.unified.data.parser.LawParser
import java.io.File

/** 法规元信息（阅读页信息卡）：标题、发文字号、发布/实施日期、发布机关、效力状态 */
data class LawMeta(
    val title: String,
    val docNo: String? = null,          // 发文字号（如"全国人民代表大会公告"）
    val publishDate: String? = null,
    val effectiveDate: String? = null,
    val organ: String? = null,
    val valid: Boolean = true
)

/**
 * 目录数据源：首次启动由 LawDataManager 把 assets/laws.zip 解压到
 * filesDir/laws/，之后从私有目录只读打开 db.sqlite3 作目录索引；
 * 法条 Markdown 也从同一私有目录读取。月度更新下载的新数据解压到同一目录即覆盖生效。
 *
 * 路径解析 resolvePath 与已验证的 xloger lawTran 逻辑一致（实测 1688/1688 命中）：
 *   刑法特例 → subTitle → fileName → publish 变体 → name 变体，最后按文件名模糊兜底。
 */
class AssetsLawDataSource(private val context: Context) {

    private val lawsDir = LawDataManager.lawsDir(context)
    private val dbFile = File(lawsDir, "db.sqlite3")
    private val folderCache = mutableMapOf<String, String>()

    /** 确保压缩数据已解压到私有目录（幂等）。 */
    fun ensureCatalogCopied() {
        LawDataManager.ensureExtracted(context)
    }

    private fun openCatalog(): SQLiteDatabase =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    fun getCategories(): List<CategoryEntity> = openCatalog().use { db ->
        val list = mutableListOf<CategoryEntity>()
        db.rawQuery(
            "SELECT id,name,folder,`group`,isSubFolder,`order` FROM category ORDER BY `order`",
            null
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    CategoryEntity(
                        id = c.string(0), name = c.string(1), folder = c.string(2),
                        group = c.nullableString(3), isSubFolder = c.int(4),
                        order = c.nullableInt(5)
                    )
                )
            }
        }
        list
    }

    fun getLaws(categoryId: String): List<LawEntity> = openCatalog().use { db ->
        mapLaws(db.rawQuery(
            "SELECT id,level,name,filename,publish,`order`,subtitle,tags,category_id,valid_to FROM law " +
                    "WHERE category_id=? ORDER BY `order`",
            arrayOf(categoryId)
        ))
    }

    fun getAllLaws(): List<LawEntity> = openCatalog().use { db ->
        mapLaws(db.rawQuery(
            "SELECT id,level,name,filename,publish,`order`,subtitle,tags,category_id,valid_to FROM law",
            null
        ))
    }

    fun getLawById(id: String): LawEntity? = openCatalog().use { db ->
        mapLaws(db.rawQuery(
            "SELECT id,level,name,filename,publish,`order`,subtitle,tags,category_id,valid_to FROM law WHERE id=?",
            arrayOf(id)
        )).firstOrNull()
    }

    /**
     * 某部法规的“版本族”：同一主体及其历次修正/修订（如「宪法」+「宪法修正案（2018年）」、
     * 「刑法」+「刑法修正案（四）」…）。
     * 识别规则：名称去掉版本后缀（修正案（YYYY年）/修正案（序号）/（YYYY年））后的主体相同即为同族；
     * 排序：带年份的按年份升序（旧→新），主体（无年份，现行）排最后。
     * 返回列表恒包含 [lawId] 自身；无其他版本时列表大小为 1。
     */
    fun getLawVersions(lawId: String): List<LawEntity> = openCatalog().use { db ->
        val self = mapLaws(db.rawQuery(
            "SELECT id,level,name,filename,publish,`order`,subtitle,tags,category_id,valid_to FROM law WHERE id=?",
            arrayOf(lawId)
        )).firstOrNull() ?: return emptyList()

        val name = self.name
        // 提取主体名：X修正案（…）/ X（YYYY年） → X；否则主体即自身
        val main = VERSION_SUFFIX_RE.find(name)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: name
        val family = mapLaws(db.rawQuery(
            "SELECT id,level,name,filename,publish,`order`,subtitle,tags,category_id,valid_to FROM law " +
                    "WHERE category_id=? AND (name=? OR name LIKE ?)",
            // "修正案%" 同时覆盖 修正案（2018年）、修正案（四）、修正案 等命名
            arrayOf(self.categoryId, main, "${main}修正案%")
        ))
        family.sortedWith(compareBy<LawEntity> { yearOf(it.name) ?: Int.MAX_VALUE }.thenBy { it.name })
    }

    /** 从名称提取年份（X（YYYY年）/ X修正案（YYYY年）→ YYYY；主体无年份 → null） */
    private fun yearOf(name: String): Int? =
        YEAR_RE.find(name)?.groupValues?.get(1)?.toIntOrNull()

    /** 全部效力层级（去重），用于检索的“标签/层级”筛选。 */
    fun getLevels(): List<String> = openCatalog().use { db ->
        val list = mutableListOf<String>()
        db.rawQuery(
            "SELECT DISTINCT level FROM law WHERE level IS NOT NULL AND level <> '' ORDER BY level",
            null
        ).use { c -> while (c.moveToNext()) list.add(c.string(0)) }
        list
    }

    private fun mapLaws(c: Cursor): List<LawEntity> {
        val list = mutableListOf<LawEntity>()
        c.use {
            while (it.moveToNext()) {
                list.add(
                    LawEntity(
                        id = it.string(0), level = it.string(1), name = it.string(2),
                        fileName = it.nullableString(3), publish = it.nullableString(4),
                        order = it.nullableInt(5), subTitle = it.nullableString(6),
                        tags = it.nullableString(7), categoryId = it.string(8),
                        validTo = it.nullableString(9)
                    )
                )
            }
        }
        return list
    }

    fun categoryFolder(categoryId: String): String {
        folderCache[categoryId]?.let { return it }
        val folder = openCatalog().use { db ->
            db.rawQuery("SELECT folder FROM category WHERE id=?", arrayOf(categoryId))
                .use { c -> if (c.moveToFirst()) c.string(0) else "" }
        }
        folderCache[categoryId] = folder
        return folder
    }

    /** 读取某部法规 Markdown 的行（供解析/索引构建复用） */
    fun readLawLines(path: String): List<String> =
        File(lawsDir, path).bufferedReader(Charsets.UTF_8).readLines()

    /** 解析某部法规的 Markdown，返回嵌套树 */
    fun parseLaw(law: LawEntity): LawGroup {
        val folder = categoryFolder(law.categoryId)
        val path = resolvePath(folder, law.name, law.fileName, law.publish, law.subTitle)
        return LawParser.parse(readLawLines(path))
    }

    /**
     * 解析法规元信息（标题后的元数据区：日期 + 机关 + 动作）。
     * 提取：发布日期（首个"通过/修订"的日期）、实施日期（"施行"日期）、发布机关。
     */
    fun parseLawMeta(law: LawEntity): LawMeta {
        val folder = categoryFolder(law.categoryId)
        val path = resolvePath(folder, law.name, law.fileName, law.publish, law.subTitle)
        val lines = readLawLines(path)
        // 元数据区：`# 标题` 之后、`<!-- INFO END -->` 或首个 `#`/正文之前
        val metaLines = mutableListOf<String>()
        var started = false
        for (line in lines) {
            val t = line.trim()
            if (t.startsWith("<!--") || t.startsWith("#")) {
                if (started && t.startsWith("<!--")) break
                if (t.startsWith("#") && t != lines.firstOrNull()?.trim()) break
                continue
            }
            if (t.isEmpty()) continue
            if (!started) started = true
            if (started) metaLines.add(t)
            if (t.contains("施行") || t.contains("通过") || t.contains("修订") || t.contains("修正")) continue
            if (metaLines.size > 6) break
        }
        val text = metaLines.joinToString("\n")
        // 发布日期：首个“通过/修订/批准”的日期
        val publishDate = Regex("(\\d{4}年\\d{1,2}月\\d{1,2}日)\\s+[^\\n]*?(通过|修订|批准)").find(text)?.groupValues?.get(1)
        // 实施日期：“施行”行的日期
        val effectiveDate = Regex("(\\d{4}年\\d{1,2}月\\d{1,2}日)\\s+[^\\n]*?施行").find(text)?.groupValues?.get(1)
        // 发布机关：通过行中的“第X届全国人民代表大会第X次会议”或“全国人民代表大会常务委员会”
        val organ = Regex("((?:第[\\d一二三四五六七八九十]+届)?全国人民代表大会(?:常务)?委员会|全国人民代表大会常务委员会)")
            .find(text)?.groupValues?.get(1) ?: "全国人民代表大会"
        // 发文字号：公告公布施行记录（如"全国人民代表大会公告公布施行" → "全国人民代表大会公告"）
        val docNo = Regex("([^\\s，。]{2,20}?公告)公布施行").find(text)?.groupValues?.get(1)
        return LawMeta(
            title = law.name,
            docNo = docNo,
            publishDate = publishDate ?: law.publish,
            effectiveDate = effectiveDate,
            organ = organ,
            valid = isLawValid(law)
        )
    }

    /** 效力判断：valid_to 为 null 或 >= 今天 → 有效 */
    private fun isLawValid(law: LawEntity): Boolean {
        val vt = law.validTo ?: return true
        if (vt >= "2099-01-01") return true
        return vt >= java.time.LocalDate.now().toString()
    }

    /**
     * 路径解析（返回相对于 lawsDir 的路径）。与已验证逻辑一致。
     */
    fun resolvePath(
        folder: String,
        name: String,
        fileName: String?,
        publish: String?,
        subTitle: String?
    ): String {
        val candidates = buildCandidatePaths(folder, name, fileName, publish, subTitle)
        for (p in candidates) {
            if (File(lawsDir, p).exists()) return p
        }
        val list = File(lawsDir, folder).list() ?: emptyArray()
        for (f in list) {
            if (f.endsWith(".md") && f.contains(name)) return "$folder/$f"
        }
        return candidates.last()
    }

    private fun Cursor.string(i: Int): String = getString(i) ?: ""
    private fun Cursor.int(i: Int): Int = getInt(i)
    private fun Cursor.nullableString(i: Int): String? =
        if (isNull(i)) null else getString(i)

    private fun Cursor.nullableInt(i: Int): Int? =
        if (isNull(i)) null else getInt(i)

    companion object {
        /** 主体提取：X修正案（…） → X（覆盖 修正案（2018年）/修正案（四）/修正案） */
        private val VERSION_SUFFIX_RE = Regex("^(.*?)修正案(?:（[^）]*）)?$")
        private val YEAR_RE = Regex("（(\\d{4})年）")
    }
}
