package com.unscroll.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.section.BlockedSection
import com.unscroll.app.domain.section.SectionBlockMode
import com.unscroll.app.ui.components.SectionHeader
import com.unscroll.app.ui.components.SegmentedControl
import com.unscroll.app.ui.components.SettingRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.section.nameRes
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme

/** Callbacks for Advanced options › Section blocking. */
internal class SectionDetailActions(
    val onMode: (SectionBlockMode) -> Unit = {},
)

/**
 * Advanced options › Section blocking, for Reels-capable apps: when the reels toggle covers the
 * section ("Always", or "After limit": only once today's daily limit is reached). The toggle
 * itself, with its setup steps and Plus, is in the quick controls at the top.
 */
@Composable
internal fun SectionBlockingDetail(state: SectionAppState, hasDailyLimit: Boolean, actions: SectionDetailActions) {
    val section = stringResource(state.section.nameRes)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        SectionHeader(
            title = stringResource(R.string.section_title),
            info = stringResource(R.string.section_detail_info, section),
        )
        SettingsGroup {
            SettingRow(
                title = stringResource(R.string.section_when, section),
                icon = R.drawable.ic_layers,
                showChevron = false,
            )
            Column(
                modifier = Modifier.padding(start = Dimens.spaceL, end = Dimens.spaceL, bottom = Dimens.spaceM),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
            ) {
                SegmentedControl(
                    options = SectionBlockMode.entries,
                    selected = state.mode,
                    label = {
                        stringResource(
                            when (it) {
                                SectionBlockMode.ALWAYS -> R.string.section_mode_always
                                SectionBlockMode.AFTER_LIMIT -> R.string.section_mode_after_limit
                            },
                        )
                    },
                    onSelect = actions.onMode,
                )
            }
            if (state.mode == SectionBlockMode.AFTER_LIMIT && !hasDailyLimit) {
                SettingRow(
                    title = stringResource(R.string.section_detail_needs_limit),
                    titleColor = UnscrollTheme.status.warn,
                    showChevron = false,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SectionBlockingDetailPreview() {
    UnscrollTheme {
        Surface {
            SectionBlockingDetail(
                state = SectionAppState(section = BlockedSection.REELS, mode = SectionBlockMode.AFTER_LIMIT),
                hasDailyLimit = false,
                actions = SectionDetailActions(),
            )
        }
    }
}
