package de.joinside.dhbw.data.storage.database


import androidx.room.Room
import androidx.room.RoomDatabase
import de.joinside.dhbw.data.storage.database.AppDatabase
import java.io.File

fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = File(System.getProperty("java.io.tmpdir"), "dhbw.db")
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
}
