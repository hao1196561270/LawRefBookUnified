package com.lawrefbook.unified.data

import androidx.room.TypeConverter

/**
 * Room 类型转换器：把 List<String>（如检索索引的面包屑路径）序列化为单字段。
 * 单独成文件，避免与 Entity 同文件时 KSP 符号解析出现 MissingType。
 */
object Converters {
    @TypeConverter
    fun fromList(list: List<String>): String = list.joinToString("\u0001")

    @TypeConverter
    fun toList(s: String): List<String> =
        if (s.isEmpty()) emptyList() else s.split("\u0001")
}
