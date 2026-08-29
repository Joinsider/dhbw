package de.fampopprol.dhbwhorb.data.storage.database


import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

// The migration policy lives in `createRoomDatabase`, not here — this only picks the file.
fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(DATABASE_FILE_NAME)
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}
