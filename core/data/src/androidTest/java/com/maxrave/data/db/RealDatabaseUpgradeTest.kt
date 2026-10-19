package com.maxrave.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Optional private fixture supplied on device; never commits or logs user records. */
class RealDatabaseUpgradeTest {
    @Test fun opensRealDatabaseWithoutChangingExistingRows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val supplied = InstrumentationRegistry.getArguments().getString("legacyDatabasePath")
        assumeTrue(supplied != null)
        val context = instrumentation.targetContext
        val name = "private-real-upgrade.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        File(supplied!!).copyTo(file, overwrite = true)
        val before = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { snapshot(it) }
        val room = Room.databaseBuilder(context, MusicDatabase::class.java, name).addTypeConverter(Converters()).build()
        try {
            room.openHelper.writableDatabase.query("PRAGMA user_version").use { assertTrue(it.moveToFirst()); assertEquals(19, it.getInt(0)) }
        } finally { room.close() }
        val after = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { snapshot(it, before.keys) }
        assertEquals("Existing tables/rows changed", before, after)
        context.deleteDatabase(name)
    }
    private fun snapshot(db: SQLiteDatabase, only: Set<String>? = null): Map<String, String> {
        val tables = mutableListOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'", null).use {
            while (it.moveToNext()) if (only == null || it.getString(0) in only) tables.add(it.getString(0))
        }
        return tables.associateWith { table ->
            val digest = MessageDigest.getInstance("SHA-256")
            val rows = mutableListOf<String>()
            db.rawQuery("SELECT * FROM \"${table.replace("\"", "\"\"")}\"", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val row = (0 until cursor.columnCount).joinToString("|") { i ->
                        when (cursor.getType(i)) {
                            0 -> "null"
                            4 -> cursor.getBlob(i).joinToString("") { "%02x".format(it) }
                            else -> cursor.getString(i).let { "${it.length}:$it" }
                        }
                    }
                    rows.add(MessageDigest.getInstance("SHA-256").digest(row.toByteArray()).joinToString("") { "%02x".format(it) })
                }
            }
            rows.sorted().forEach { digest.update(it.toByteArray()) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
