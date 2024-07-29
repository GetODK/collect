package org.odk.collect.android.entities

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.provider.BaseColumns._ID
import androidx.core.database.sqlite.transaction
import org.odk.collect.androidshared.sqlite.CursorExt.first
import org.odk.collect.androidshared.sqlite.CursorExt.foldAndClose
import org.odk.collect.androidshared.sqlite.CursorExt.getInt
import org.odk.collect.androidshared.sqlite.CursorExt.getIntOrNull
import org.odk.collect.androidshared.sqlite.CursorExt.getString
import org.odk.collect.androidshared.sqlite.CursorExt.getStringOrNull
import org.odk.collect.androidshared.sqlite.DatabaseConnection
import org.odk.collect.androidshared.sqlite.DatabaseMigrator
import org.odk.collect.androidshared.sqlite.SQLiteDatabaseExt.delete
import org.odk.collect.androidshared.sqlite.SQLiteDatabaseExt.query
import org.odk.collect.entities.storage.EntitiesRepository
import org.odk.collect.entities.storage.Entity

private object ListsTable {
    const val TABLE_NAME = "lists"
    const val COLUMN_NAME = "name"
}

private object EntitiesTable {
    const val COLUMN_ID = "id"
    const val COLUMN_LABEL = "label"
    const val COLUMN_VERSION = "vesion"
    const val COLUMN_TRUNK_VERSION = "trunk_version"
    const val COLUMN_BRANCH_ID = "branch_id"
    const val COLUMN_STATE = "state"
}

class DatabaseEntitiesRepository(context: Context, dbPath: String) : EntitiesRepository {

    private val databaseConnection: DatabaseConnection = DatabaseConnection(
        context,
        dbPath,
        "entities.db",
        EntitiesDatabaseMigrator(),
        1,
        true
    )

    override fun save(vararg entities: Entity) {
        val existingLists = getLists()
        val createdLists = mutableListOf<String>()
        val modifiedList = mutableListOf<String>()

        databaseConnection.writeableDatabase.transaction {
            entities.forEach { entity ->
                val list = entity.list
                if (!existingLists.contains(list) && !createdLists.contains(list)) {
                    createList(list)
                    createdLists.add(list)
                }

                if (!modifiedList.contains(list)) {
                    updateProperties(entity)
                    modifiedList.add(list)
                }

                val existing = if (existingLists.contains(list)) {
                    query(
                        list,
                        "${EntitiesTable.COLUMN_ID} = ?",
                        arrayOf(entity.id)
                    ).first { mapCursorRowToEntity(list, it, 0) }
                } else {
                    null
                }

                if (existing != null) {
                    val state = if (existing.state == Entity.State.OFFLINE) {
                        entity.state
                    } else {
                        Entity.State.ONLINE
                    }

                    val contentValues = ContentValues().also {
                        it.put(EntitiesTable.COLUMN_ID, entity.id)
                        it.put(EntitiesTable.COLUMN_LABEL, entity.label ?: existing.label)
                        it.put(EntitiesTable.COLUMN_VERSION, entity.version)
                        it.put(EntitiesTable.COLUMN_TRUNK_VERSION, entity.trunkVersion)
                        it.put(EntitiesTable.COLUMN_BRANCH_ID, entity.branchId)
                        it.put(EntitiesTable.COLUMN_STATE, state.id)

                        entity.properties.forEach { (name, value) ->
                            it.put(name, value)
                        }
                    }

                    update(
                        list,
                        contentValues,
                        "${EntitiesTable.COLUMN_ID} = ?",
                        arrayOf(entity.id)
                    )
                } else {
                    val contentValues = ContentValues().also {
                        it.put(EntitiesTable.COLUMN_ID, entity.id)
                        it.put(EntitiesTable.COLUMN_LABEL, entity.label)
                        it.put(EntitiesTable.COLUMN_VERSION, entity.version)
                        it.put(EntitiesTable.COLUMN_TRUNK_VERSION, entity.trunkVersion)
                        it.put(EntitiesTable.COLUMN_BRANCH_ID, entity.branchId)
                        it.put(EntitiesTable.COLUMN_STATE, entity.state.id)

                        entity.properties.forEach { (name, value) ->
                            it.put(name, value)
                        }
                    }

                    insertOrThrow(
                        list,
                        null,
                        contentValues
                    )
                }
            }
        }

        updateRowNumbers()
    }

    override fun getLists(): Set<String> {
        return databaseConnection
            .readableDatabase
            .query(ListsTable.TABLE_NAME)
            .foldAndClose(emptySet()) { set, cursor -> set + cursor.getString(ListsTable.COLUMN_NAME) }
    }

    override fun getEntities(list: String): List<Entity.Saved> {
        if (!listExists(list)) {
            return emptyList()
        }

        return databaseConnection.readableDatabase
            .rawQuery(
                """
                SELECT *, i.rowid
                FROM $list e, ${list}_row_numbers i
                WHERE e._id = i._id
                """.trimIndent(),
                null
            )
            .foldAndClose(emptyList()) { entities, cursor ->
                entities + mapCursorRowToEntity(
                    list,
                    cursor,
                    cursor.getInt("rowid")
                )
            }
    }

    override fun clear() {
        getLists().forEach {
            databaseConnection.writeableDatabase.delete(it)
        }

        databaseConnection.writeableDatabase.delete(ListsTable.TABLE_NAME)
    }

    override fun addList(list: String) {
        createList(list)
        updateRowNumbers()
    }

    override fun delete(id: String) {
        getLists().forEach {
            databaseConnection.writeableDatabase.delete(
                it,
                "${EntitiesTable.COLUMN_ID} = ?",
                arrayOf(id)
            )
        }

        updateRowNumbers()
    }

    override fun getById(list: String, id: String): Entity.Saved? {
        if (!listExists(list)) {
            return null
        }

        return databaseConnection.readableDatabase
            .rawQuery(
                """
                SELECT *, i.rowid
                FROM $list e, ${list}_row_numbers i
                WHERE e._id = i._id AND e.id = ?
                """.trimIndent(),
                arrayOf(id)
            ).first {
                mapCursorRowToEntity(list, it, it.getInt("rowid"))
            }
    }

    override fun getAllByProperty(
        list: String,
        property: String,
        value: String
    ): List<Entity.Saved> {
        if (!listExists(list)) {
            return emptyList()
        }

        return databaseConnection.readableDatabase
            .rawQuery(
                """
                SELECT *, i.rowid
                FROM $list e, ${list}_row_numbers i
                WHERE e._id = i._id AND e.$property = ?
                """.trimIndent(),
                arrayOf(value)
            ).foldAndClose(emptyList()) { entities, cursor ->
                entities + mapCursorRowToEntity(
                    list,
                    cursor,
                    cursor.getInt("rowid")
                )
            }
    }

    private fun listExists(list: String): Boolean {
        return databaseConnection.readableDatabase
            .query(
                ListsTable.TABLE_NAME,
                selection = "${ListsTable.COLUMN_NAME} = ?",
                selectionArgs = arrayOf(list)
            ).use { it.count } > 0
    }

    private fun createList(list: String) {
        databaseConnection.writeableDatabase.transaction {
            val contentValues = ContentValues()
            contentValues.put(ListsTable.COLUMN_NAME, list)
            insertOrThrow(
                ListsTable.TABLE_NAME,
                null,
                contentValues
            )

            execSQL(
                """
                    CREATE TABLE IF NOT EXISTS $list (
                        $_ID integer PRIMARY KEY,
                        ${EntitiesTable.COLUMN_ID} text,
                        ${EntitiesTable.COLUMN_LABEL} text,
                        ${EntitiesTable.COLUMN_VERSION} integer,
                        ${EntitiesTable.COLUMN_TRUNK_VERSION} integer,
                        ${EntitiesTable.COLUMN_BRANCH_ID} text,
                        ${EntitiesTable.COLUMN_STATE} integer NOT NULL
                    );
                """.trimIndent()
            )

            execSQL(
                """
                CREATE UNIQUE INDEX ${list}_unique_id_index ON $list (${EntitiesTable.COLUMN_ID});
                """.trimIndent()
            )
        }
    }

    private fun updateProperties(entity: Entity) {
        entity.properties.map { it.first }.forEach {
            try {
                databaseConnection.writeableDatabase.execSQL(
                    """
                    ALTER TABLE ${entity.list} ADD $it text;
                    """.trimIndent()
                )
            } catch (e: SQLiteException) {
                println(e)
            }
        }
    }

    private fun updateRowNumbers() {
        getLists().forEach {
            databaseConnection.writeableDatabase.execSQL(
                """
                DROP TABLE IF EXISTS ${it}_row_numbers;
                """.trimIndent()
            )

            databaseConnection.writeableDatabase.execSQL(
                """
                CREATE TABLE ${it}_row_numbers AS SELECT _id FROM $it;
                """.trimIndent()
            )
        }
    }

    private fun mapCursorRowToEntity(
        list: String,
        cursor: Cursor,
        rowId: Int
    ): Entity.Saved {
        val propertyColumns = cursor.columnNames.filter {
            !listOf(
                _ID,
                EntitiesTable.COLUMN_ID,
                EntitiesTable.COLUMN_LABEL,
                EntitiesTable.COLUMN_VERSION,
                EntitiesTable.COLUMN_TRUNK_VERSION,
                EntitiesTable.COLUMN_BRANCH_ID,
                EntitiesTable.COLUMN_STATE,
                "rowid"
            ).contains(it)
        }

        val properties =
            propertyColumns.fold(emptyList<Pair<String, String>>()) { accum, property ->
                accum + Pair(property, cursor.getStringOrNull(property) ?: "")
            }

        return Entity.Saved(
            list,
            cursor.getString(EntitiesTable.COLUMN_ID),
            cursor.getStringOrNull(EntitiesTable.COLUMN_LABEL),
            cursor.getInt(EntitiesTable.COLUMN_VERSION),
            properties,
            Entity.State.fromId(cursor.getInt(EntitiesTable.COLUMN_STATE)),
            rowId - 1,
            cursor.getIntOrNull(EntitiesTable.COLUMN_TRUNK_VERSION),
            cursor.getString(EntitiesTable.COLUMN_BRANCH_ID)
        )
    }
}

class EntitiesDatabaseMigrator :
    DatabaseMigrator {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${ListsTable.TABLE_NAME} (
                    $_ID integer PRIMARY KEY, 
                    ${ListsTable.COLUMN_NAME} text NOT NULL
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int) {
        TODO("Not yet implemented")
    }

    override fun onDowngrade(db: SQLiteDatabase?) {
        TODO("Not yet implemented")
    }
}