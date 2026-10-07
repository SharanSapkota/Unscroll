package com.unscroll.app.data.history

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.withTransaction
import com.unscroll.app.data.db.UnscrollDatabase
import com.unscroll.app.data.db.toSession
import com.unscroll.app.data.scroll.ScrollCountingPreferences
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.export.SessionCsv
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.service.SessionManager
import com.unscroll.app.service.TrackingService
import com.unscroll.app.util.appLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * "Your data" (M8): export sessions as CSV to a file the user picks, delete the usage history
 * (settings, limits, goal and consent stay), or delete all data (everything, like a fresh install).
 */
@Singleton
class HistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: UnscrollDatabase,
    private val sessionManager: SessionManager,
    private val scrollPreferences: ScrollCountingPreferences,
    private val scrollCounting: ScrollCountingRepository,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {
    /** Writes every session as CSV to [uri] (from the system "create document" picker). Returns the row count. */
    suspend fun exportSessions(uri: Uri): Int = withContext(Dispatchers.IO) {
        val sessions = database.sessionDao().getAll().map { it.toSession() }
        val labels = HashMap<String, String>()
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open $uri")
        stream.bufferedWriter().use { writer ->
            SessionCsv.write(
                sessions = sessions,
                appName = { pkg -> labels.getOrPut(pkg) { context.packageManager.appLabel(pkg) } },
                zone = ZoneId.systemDefault(),
                now = clock.now(),
                out = writer,
            )
        }
        sessions.size
    }

    /**
     * Deletes sessions, the nudge log and the extension log. Ends the open session
     * first, so the tracker doesn't keep writing to a deleted row. Returns the deleted session count.
     */
    suspend fun deleteUsageHistory(): Int {
        sessionManager.endCurrentSession()
        val deleted = database.withTransaction {
            database.frictionDao().deleteAllNudges()
            database.blockingDao().deleteAllOverrides()
            database.sessionDao().deleteAll()
        }
        scrollPreferences.resetHistory(clock.now(), countingNow = scrollCounting.connected.value)
        return deleted
    }

    /**
     * "Delete all my data": ends the open session and stops tracking, then empties every table
     * and every preference. The onboarding flag goes too, so the app starts over with onboarding,
     * and tracking only starts again once it is finished.
     */
    suspend fun deleteAllData() = withContext(NonCancellable) {
        // Not cancellable: the screen that asked closes as soon as the onboarding flag is gone.
        sessionManager.endCurrentSession()
        TrackingService.stop(context)
        wipeAllStorage(database, dataStore)
    }
}
