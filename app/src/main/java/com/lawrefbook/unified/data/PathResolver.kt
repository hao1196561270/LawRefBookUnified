package com.lawrefbook.unified.data

/**
 * 纯函数：根据目录字段构造法条 Markdown 的候选路径列表（顺序即优先级）。
 * 与已验证的 xloger lawTran 逻辑一致（实测 1688/1688 命中）：
 *   1) 刑法特例：folder/name.md 优先（刑法分则文件名形如 刑法/刑法.md）
 *   2) subTitle
 *   3) fileName
 *   4) name(publish) 变体
 *   5) name 兜底
 * 该函数不依赖 Android，便于单元测试。实际文件存在性检查在 AssetsLawDataSource 中完成。
 */
fun buildCandidatePaths(
    folder: String,
    name: String,
    fileName: String?,
    publish: String?,
    subTitle: String?
): List<String> = buildList {
    if (folder == "刑法") add("$folder/$name.md")
    subTitle?.let { add("$folder/$it.md") }
    fileName?.let { add("$folder/$it.md") }
    publish?.let { add("$folder/$name($it).md") }
    add("$folder/$name.md")
}
