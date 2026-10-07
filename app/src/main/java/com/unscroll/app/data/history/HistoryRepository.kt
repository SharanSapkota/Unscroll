package com.unscroll.app.data.history

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.unscroll.app.data.db.UnscrollDatabase
import com.unscroll.app.data.db.toSession
import com.unscroll.app.data.scroll.ScrollCountingPreferences
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.export.SessionCsv
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.service.SessionManager
import com.unscroll.app.util.appLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Your data" (M8): export sessions as CSV to a file the user picks, and delete the usage history.
 * Settings (limits, friction, overlay, goal, scroll-counting consent) are kept on purpose: removing
 * them is what uninstalling or "Clear storage" is for.
 */
@Singleton
class HistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: UnscrollDatabase,
    private val sessionManager: SessionManager,
    private val scrollPreferences: ScrollCountingPreferences,
    private val scrollCounting: ScrollCountingRepository,
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
}
