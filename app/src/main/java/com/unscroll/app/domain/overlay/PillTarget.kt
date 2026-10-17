package com.unscroll.app.domain.overlay

import com.unscroll.app.domain.reels.ReelsLive
import com.unscroll.app.domain.session.ActiveSegment
import com.unscroll.app.domain.session.ActiveSession
import com.unscroll.app.domain.session.ScreenSection

/** What the pill follows right now: nothing, the whole app (a session), or Reels (a segment). */
sealed interface PillTarget {
    data object None : PillTarget

    /** The usual pill: today's total in the app, while it is in the foreground. */
    data class App(val session: ActiveSession) : PillTarget

    /** Reels-only timer: today's time in Reels, only while the open segment is in Reels. */
    data class Reels(val segment: ActiveSegment) : PillTarget

    companion object {
        /**
         * An app on the Reels-only timer ([ReelsLive.reelsOnly]) gets the pill only while its open
         * segment is in Reels; any other screen (or no confirmed section yet) hides it. Every other
         * app, and one that fell back, gets the whole-app pill as before.
         */
        fun of(session: ActiveSession?, segment: ActiveSegment?, live: ReelsLive?): PillTarget {
            if (session == null) return None
            if (live == null || !live.reelsOnly || live.packageName != session.packageName) return App(session)
            val inReels = segment?.takeIf { it.sessionId == session.id && it.section == ScreenSection.REELS }
            return if (inReels != null) Reels(inReels) else None
        }
    }
}
