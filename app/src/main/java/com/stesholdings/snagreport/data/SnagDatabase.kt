package com.stesholdings.snagreport.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [SnagEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class SnagDatabase : RoomDatabase() {

    abstract fun snagDao(): SnagDao

    companion object {
        @Volatile
        private var instance: SnagDatabase? = null

        fun getInstance(context: Context): SnagDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SnagDatabase::class.java,
                    "snag_report.db"
                ).build().also { instance = it }
            }
    }
}
