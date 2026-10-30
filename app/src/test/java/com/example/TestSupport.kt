package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.concordance.data.ConcordanceDatabase
import com.example.concordance.ui.ConcordanceViewModel
import kotlinx.coroutines.runBlocking

/**
 * Shared scaffolding for the Robolectric tests.
 *
 * The rules the rest of the suite follows:
 *
 *  * a test that needs a database uses an **in-memory** Room database, so it
 *    never sees another test's rows;
 *  * a test that needs the view model uses the app's own sandbox database (the
 *    one `ConcordanceViewModel` opens), which is cleared by [clearSandboxData];
 *  * a test that talks to a third-party HTTP API uses MockWebServer, never the
 *    network.
 */

/** The Robolectric application instance for the running test. */
fun testApp(): Application = ApplicationProvider.getApplicationContext()

/** A fresh, empty, in-memory database. Close it when the test ends. */
fun inMemoryDatabase(): ConcordanceDatabase =
    Room.inMemoryDatabaseBuilder(testApp(), ConcordanceDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** A view model over the app's own (sandboxed) database. */
fun testViewModel(): ConcordanceViewModel = ConcordanceViewModel(testApp())

/**
 * Empty every table in the app's own database.
 *
 * The view model opens the database through a process-wide singleton, so the
 * rows one test writes are still there for the next one. Clearing between
 * tests keeps them independent.
 */
fun clearSandboxData() {
    val db = ConcordanceDatabase.getDatabase(testApp())
    val sql = db.openHelper.writableDatabase
    for (table in listOf(
        "claims", "capsules", "challenges", "provenance_records",
        "response_cache", "model_revisions", "learning_audit", "sync_cursor"
    )) {
        sql.execSQL("DELETE FROM $table")
    }
}
