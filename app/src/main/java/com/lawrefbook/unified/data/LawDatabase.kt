package com.lawrefbook.unified.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [FavoritesEntity::class, HistoryEntity::class, LawItemEntity::class],
    version = 2,
    exportSchema = false
)
abstract class LawDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun historyDao(): HistoryDao
    abstract fun lawItemDao(): LawItemDao

    companion object {
        /** law_item 增加 level/publish/tags/categoryId 字段（检索筛选与排序用）。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE law_item ADD COLUMN level TEXT")
                db.execSQL("ALTER TABLE law_item ADD COLUMN publish TEXT")
                db.execSQL("ALTER TABLE law_item ADD COLUMN tags TEXT")
                db.execSQL("ALTER TABLE law_item ADD COLUMN categoryId TEXT")
            }
        }

        @Volatile
        private var INSTANCE: LawDatabase? = null

        fun get(context: Context): LawDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LawDatabase::class.java,
                    "lawrefbook_user.db"
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
