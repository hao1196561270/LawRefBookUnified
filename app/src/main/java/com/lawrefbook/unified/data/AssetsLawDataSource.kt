package com.lawrefbook.unified.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.lawrefbook.unified.data.model.CategoryEntity
import com.lawrefbook.unified.data.model.LawEntity
import com.lawrefbook.unified.data.model.LawGroup
import com.lawrefbook.unified.data.parser.LawParser
import java.io.File

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
            "SELECT id,level,name,filename,publish,`order`,subtitle,tags,category_id FROM law " +
                    "WHERE category_id=? ORDER BY `order`",
            arrayOf(categoryId)
        ))
    }

    fun getAllLaws(): List<LawEntity> = openCatalog().use { db ->
        mapLaws(db.rawQuery(
            "SELECT id,level,name,filename,publish,`order`,subtitle,tags,category_id FROM law",
            null
        ))
    }

    fun getLawById(id: String): LawEntity? = openCatalog().use { db ->
        mapLaws(db.rawQuery(
            "SELECT id,level,name,filename,publish,`order`,subtitle,tags,category_id FROM law WHERE id=?",
            arrayOf(id)
        )).firstOrNull()
    }

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
                        tags = it.nullableString(7), categoryId = it.string(8)
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
}
