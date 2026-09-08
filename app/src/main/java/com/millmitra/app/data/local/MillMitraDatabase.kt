package com.millmitra.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun fromEntryType(value: EntryType): String = value.name

    @TypeConverter
    fun toEntryType(value: String): EntryType = runCatching {
        EntryType.valueOf(value)
    }.getOrDefault(EntryType.TRANSLATE_DECODE)
}

@Database(entities = [MillEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class MillMitraDatabase : RoomDatabase() {

    abstract fun millEntryDao(): MillEntryDao

    companion object {
        @Volatile
        private var INSTANCE: MillMitraDatabase? = null

        const val DATABASE_NAME = "millmitra_db"

        fun getDatabase(context: Context): MillMitraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MillMitraDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Computes the epoch timestamp for exactly 7 days prior to right now.
         */
        fun getSevenDayCutoffTimestamp(): Long {
            val sevenDaysInMillis = 7L * 24L * 60L * 60L * 1000L
            return System.currentTimeMillis() - sevenDaysInMillis
        }
    }
}
