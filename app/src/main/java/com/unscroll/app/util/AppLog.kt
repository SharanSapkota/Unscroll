package com.unscroll.app.util

import android.util.Log
import com.unscroll.app.BuildConfig
import com.unscroll.app.domain.diagnostics.DiagnosticsLog

/**
 * Logging for the overlay, detection and tracking paths. Warnings and errors go to logcat and to
 * an in-memory ring buffer ([buffer], the last 200 lines) that "Copy diagnostics" can show and
 * share when the user asks; info lines reach logcat only in debug builds. Messages must stay
 * technical (tags, states, package names, exception classes): never screen text or content.
 */
object AppLog {
    val buffer = DiagnosticsLog()

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
        buffer.add(System.currentTimeMillis(), 'I', tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        Log.w(tag, message, error)
        buffer.add(System.currentTimeMillis(), 'W', tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        buffer.add(System.currentTimeMillis(), 'E', tag, message, error)
    }
}
