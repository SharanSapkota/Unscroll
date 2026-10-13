package com.unscroll.app.data.insights

import android.content.Context
import android.util.Log
import com.unscroll.app.domain.insights.InsightBank
import com.unscroll.app.domain.insights.InsightBankParser
import com.unscroll.app.domain.insights.InsightEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The Insights template bank, bundled in assets/insights (generated from docs/insight-templates.md;
 * the texts live there, not in code, so they can be translated). Read once, off the main thread.
 */
@Singleton
class InsightBankRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var cached: InsightEngine? = null

    /** The engine over the bank; an empty bank if the files can't be read (no messages, no crash). */
    suspend fun engine(): InsightEngine = mutex.withLock {
        cached ?: InsightEngine(load()).also { cached = it }
    }

    private suspend fun load(): InsightBank = withContext(Dispatchers.IO) {
        try {
            InsightBankParser.parse(read(TEMPLATES), read(PHRASES))
        } catch (e: IOException) {
            Log.w(TAG, "Could not read the insight bank", e)
            InsightBank.EMPTY
        }
    }

    private fun read(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    private companion object {
        const val TAG = "Insights"
        const val TEMPLATES = "insights/templates.tsv"
        const val PHRASES = "insights/phrases.tsv"
    }
}
