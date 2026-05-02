package com.jrgames.blutdruck.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MeasurementSession::class],
    version = 2,
    exportSchema = false,
)
abstract class BlutdruckDatabase : RoomDatabase() {

    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile
        private var INSTANCE: BlutdruckDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS measurement_sessions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestampMillis INTEGER NOT NULL,
                        arm TEXT NOT NULL,
                        sys1 INTEGER NOT NULL,
                        dia1 INTEGER NOT NULL,
                        pulse1 INTEGER NOT NULL,
                        sys2 INTEGER,
                        dia2 INTEGER,
                        pulse2 INTEGER,
                        sys3 INTEGER,
                        dia3 INTEGER,
                        pulse3 INTEGER,
                        note TEXT
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO measurement_sessions_new (
                        id, timestampMillis, arm,
                        sys1, dia1, pulse1,
                        sys2, dia2, pulse2,
                        sys3, dia3, pulse3,
                        note
                    )
                    SELECT
                        id, timestampMillis, arm,
                        sys1, dia1, pulse1,
                        sys2, dia2, pulse2,
                        sys3, dia3, pulse3,
                        NULL
                    FROM measurement_sessions
                """.trimIndent())

                db.execSQL("DROP TABLE measurement_sessions")
                db.execSQL("ALTER TABLE measurement_sessions_new RENAME TO measurement_sessions")
            }
        }

        fun getInstance(context: Context): BlutdruckDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BlutdruckDatabase::class.java,
                    "blutdruck_db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
