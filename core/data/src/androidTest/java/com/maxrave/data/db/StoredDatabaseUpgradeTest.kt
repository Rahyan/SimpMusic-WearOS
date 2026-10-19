package com.maxrave.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Builds an old on-disk database from the committed schema, then opens it through real Room migrations. */
class StoredDatabaseUpgradeTest {
    @Test fun version18UpgradePreservesOfflineLibrary() = checkVersion(18)
    @Test fun version19ExistingDatabaseOpensWithoutReplacingLibrary() = checkVersion(19)

    private fun checkVersion(version: Int) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "upgrade-fixture-$version.db"
        context.deleteDatabase(name)
        val schema = JSONObject(instrumentation.context.assets.open("com.maxrave.data.db.MusicDatabase/$version.json")
            .bufferedReader().use { it.readText() }).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                if (table == "song") {
                    val values = ContentValues()
                    val fields = entity.getJSONArray("fields")
                    for (j in 0 until fields.length()) {
                        val field = fields.getJSONObject(j)
                        if (field.optBoolean("notNull", false)) {
                            val column = field.getString("columnName")
                            if (field.getString("affinity") == "TEXT") values.put(column, "") else values.put(column, 0)
                        }
                    }
                    values.put("videoId", "preserved-song"); values.put("title", "My downloaded favorite")
                    values.put("liked", 1); values.put("downloadState", 3); values.put("totalPlayTime", 12345)
                    values.put("favoriteAt", 1700000000000L); values.put("downloadedAt", 1700000000000L)
                    check(sqlite.insertOrThrow("song", null, values) > 0)
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
            sqlite.version = version
        }
        val database = Room.databaseBuilder(context, MusicDatabase::class.java, name).addTypeConverter(Converters()).build()
        try {
            val song = database.getDatabaseDao().getSong("preserved-song")!!
            assertEquals("My downloaded favorite", song.title); assertTrue(song.liked)
            assertEquals(3, song.downloadState); assertEquals(12345L, song.totalPlayTime)
            assertEquals(1700000000000L, song.downloadedAt!!.toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
            assertEquals(1700000000000L, song.favoriteAt!!.toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
