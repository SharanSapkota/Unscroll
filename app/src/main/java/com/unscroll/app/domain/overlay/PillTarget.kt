package com.unscroll.app.domain.overlay

import com.unscroll.app.domain.reels.ReelsLive
import com.unscroll.app.domain.reels.SectionGate
import com.unscroll.app.domain.session.ActiveSegment
import com.unscroll.app.domain.session.ActiveSession
import com.unscroll.app.domain.session.ScreenSection

/** What the pill follows right now. */
sealed interface PillTarget {
    data object None : PillTarget

    /** The usual pill: today's total in the app, ticking while it is in the foreground. */
    data class App(val session: ActiveSession) : PillTarget

    /** Instagram / TikTok in Reels: today's time in Reels, ticking with the open REELS segment. */
    data class Reels(val segment: ActiveSegment) : PillTarget

    /**
     * Instagram / TikTok on any other screen (or Reels not confirmed yet): today's Reels figures,
     * frozen. The pill is dimmed or invisible ("Timer visibility"), but its window stays.
     */
    data class ReelsPaused(val session: ActiveSession) : PillTarget

    val packageName: String?
        get() = when (this) {
            is App -> session.packageName
            is Reels -> segment.packageName
            is ReelsPaused -> session.packageName
            None -> null
        }

    companion object {
        /**
         * Apps that aren't Reels-capable, and Reels-capable apps whose detection is unavailable
         * (fail open), get the whole-app pill. Otherwise the pill follows Reels: ticking while the
         * open segment is REELS, paused on every other screen.
         */
        fun of(session: ActiveSession?, segment: ActiveSegment?, live: ReelsLive?): PillTarget {
            if (session == null) return None
            return when (SectionGate.of(session.packageName, live)) {
                SectionGate.WHOLE_APP, SectionGate.DETECTION_UNAVAILABLE -> App(session)
                SectionGate.IN_REELS, SectionGate.OFF_REELS -> {
                    val inReels = segment?.takeIf { it.sessionId == session.id && it.section == ScreenSection.REELS }
                    if (inReels != null) Reels(inReels) else ReelsPaused(session)
                }
            }
        }
    }
}
