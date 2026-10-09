package com.unscroll.app.ui.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import com.unscroll.app.ui.components.SettingSwitchRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme

/**
 * Debug builds only (Settings › Debug): the Section Inspector. While on, the section service
 * logs identifiers of every tracked app's screens to logcat (tag "SectionInspector") and shows a
 * floating "Mark" button to tag what is on screen. Needs section blocking set up (consent and
 * the service on); Plus and the kill switch don't matter.
 */
@Composable
fun SectionInspectorScreen(onBack: () -> Unit, viewModel: SectionBlockingViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    SectionInspectorContent(
        inspector = settings.inspector,
        serviceRunning = status == ScrollCountingStatus.ACTIVE,
        onInspector = viewModel::setInspector,
        onBack = onBack,
    )
}

@Composable
private fun SectionInspectorContent(
    inspector: Boolean,
    serviceRunning: Boolean,
    onInspector: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceL),
    ) {
        IconButton(onClick = onBack) {
            Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.cd_back))
        }
        Text(
            text = stringResource(R.string.inspector_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        SettingsGroup {
            SettingSwitchRow(
                title = stringResource(R.string.inspector_switch),
                icon = R.drawable.ic_bug,
                checked = inspector,
                onCheckedChange = onInspector,
                subtitle = if (serviceRunning) null else stringResource(R.string.inspector_needs_service),
                subtitleColor = UnscrollTheme.status.warn,
            )
        }
        Text(
            text = stringResource(R.string.inspector_how),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@PreviewLightDark
@Composable
private fun SectionInspectorPreview() {
    UnscrollTheme {
        Surface { SectionInspectorContent(inspector = true, serviceRunning = false, onInspector = {}, onBack = {}) }
    }
}
