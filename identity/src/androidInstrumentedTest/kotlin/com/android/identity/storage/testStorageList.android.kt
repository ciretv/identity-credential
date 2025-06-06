package com.android.identity.storage

import android.app.Instrumentation
import androidx.test.platform.app.InstrumentationRegistry
import com.android.identity.storage.android.AndroidStorage
import com.android.identity.storage.ephemeral.EphemeralStorage
import kotlinx.datetime.Clock
import java.io.File

/**
 * Creates a list of empty [Storage] objects for testing.
 */
actual fun createTransientStorageList(testClock: Clock): List<Storage> {
    return listOf<Storage>(
        EphemeralStorage(testClock),
        /*
        TODO: this can be enabled once SqliteStorage is moved into commonMain
        com.android.identity.storage.sqlite.SqliteStorage(
            connection = AndroidSQLiteDriver().open(":memory:"),
            clock = testClock
        ),
        com.android.identity.storage.sqlite.SqliteStorage(
            connection = BundledSQLiteDriver().open(":memory:"),
            clock = testClock,
            // bundled sqlite crashes when used with Dispatchers.IO
            coroutineContext = newSingleThreadContext("DB")
        ),
         */
        AndroidStorage(
            databasePath = null,
            clock = testClock,
            keySize = 3
        )
    )
}

val knownNames = mutableSetOf<String>()

actual fun createPersistentStorage(name: String, testClock: Clock): Storage? {
    val context = InstrumentationRegistry.getInstrumentation().context
    val dbFile = context.getDatabasePath("$name.db")
    if (knownNames.add(name)) {
        dbFile.delete()
    }
    return AndroidStorage(
        databasePath = dbFile.absolutePath,
        clock = testClock,
        keySize = 3
    )
}