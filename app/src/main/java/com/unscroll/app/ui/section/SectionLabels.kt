package com.unscroll.app.ui.section

import androidx.annotation.StringRes
import com.unscroll.app.R
import com.unscroll.app.domain.section.BlockedSection

/** "Reels", "For You", "Shorts". */
@get:StringRes
val BlockedSection.nameRes: Int
    get() = when (this) {
        BlockedSection.REELS -> R.string.section_name_reels
        BlockedSection.FOR_YOU -> R.string.section_name_for_you
        BlockedSection.SHORTS -> R.string.section_name_shorts
    }

/** "Reels are blocked." */
@get:StringRes
val BlockedSection.coverTitleRes: Int
    get() = when (this) {
        BlockedSection.REELS -> R.string.section_cover_title_reels
        BlockedSection.FOR_YOU -> R.string.section_cover_title_for_you
        BlockedSection.SHORTS -> R.string.section_cover_title_shorts
    }

/** "Chat is open." (apps with chat) or "Everything else is open." */
@get:StringRes
val BlockedSection.coverOpenRes: Int
    get() = when (this) {
        BlockedSection.REELS, BlockedSection.FOR_YOU -> R.string.section_cover_open_chat
        BlockedSection.SHORTS -> R.string.section_cover_open_rest
    }

/** "Take me back to chat" or "Take me back". */
@get:StringRes
val BlockedSection.coverBackRes: Int
    get() = when (this) {
        BlockedSection.REELS, BlockedSection.FOR_YOU -> R.string.section_cover_back_chat
        BlockedSection.SHORTS -> R.string.section_cover_back
    }
