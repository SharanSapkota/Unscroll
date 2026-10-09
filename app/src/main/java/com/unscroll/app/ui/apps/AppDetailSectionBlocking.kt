package com.unscroll.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.section.BlockedSection
import com.unscroll.app.domain.section.SectionBlockMode
import com.unscroll.app.ui.components.SectionHeader
import com.unscroll.app.ui.components.SegmentedControl
import com.unscroll.app.ui.components.SettingRow
import com.unscroll.app.ui.components.SettingSwitchRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.section.nameRes
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings
import com.unscroll.app.util.openSettings

/** Callbacks for App detail › Section blocking. */
internal class SectionDetailActions(
    val onBlocked: (Boolean) -> Unit = {},
    val onMode: (SectionBlockMode) -> Unit = {},
    val onOpenPlus: () -> Unit = {},
    /** The disclosure (consent comes first). */
    val onSetUp: () -> Unit = {},
)

/**
 * App detail › Section blocking, right under Limits: "Block Reels" for this app, and when
 * ("Always" or "After limit"). Free users see the Plus badge and get the paywall; without the
 * service set up, the row leads to the disclosure or Accessibility settings.
 */
@Composable
internal fun SectionBlockingDetail(state: SectionAppState, hasDailyLimit: Boolean, actions: SectionDetailActions) {
    val context = LocalContext.current
    val section = stringResource(state.section.nameRes)
    val title = stringResource(R.string.section_block_app, section)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        SectionHeader(
            title = stringResource(R.string.section_title),
            info = stringResource(R.string.section_detail_info, section),
        )
        SettingsGroup {
            when {
                !state.isPlus -> SettingRow(
                    title = title,
                    icon = R.drawable.ic_layers,
                    value = stringResource(R.string.plus_badge),
                    onClick = actions.onOpenPlus,
                )
                !state.consented -> SettingRow(
                    title = title,
                    icon = R.drawable.ic_layers,
                    subtitle = stringResource(R.string.section_detail_set_up),
                    onClick = actions.onSetUp,
                )
                !state.serviceOn -> SettingRow(
                    title = title,
                    icon = R.drawable.ic_layers,
                    subtitle = stringResource(R.string.section_detail_enable_service),
                    subtitleColor = UnscrollTheme.status.warn,
                    onClick = { context.openSettings(SystemSettings.accessibility()) },
                )
                else -> {
                    SettingSwitchRow(
                        title = title,
                        icon = R.drawable.ic_layers,
                        checked = state.blocked,
                        onCheckedChange = actions.onBlocked,
                        subtitle = when {
                            state.turnedOff -> stringResource(R.string.section_detail_turned_off)
                            !state.ready -> stringResource(R.string.section_detail_not_ready)
                            else -> null
                        },
                        subtitleColor = UnscrollTheme.status.warn,
                    )
                    if (state.blocked) {
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
                                subtitleColor = UnscrollTheme.status.warn,
                                titleColor = UnscrollTheme.status.warn,
                                showChevron = false,
                            )
                        }
                    }
                }
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
                state = SectionAppState(
                    section = BlockedSection.REELS,
                    ready = false,
                    isPlus = true,
                    consented = true,
                    serviceOn = true,
                    turnedOff = false,
                    blocked = true,
                    mode = SectionBlockMode.AFTER_LIMIT,
                ),
                hasDailyLimit = false,
                actions = SectionDetailActions(),
            )
        }
    }
}
