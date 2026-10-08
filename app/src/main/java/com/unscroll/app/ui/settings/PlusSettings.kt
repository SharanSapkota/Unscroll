package com.unscroll.app.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.unscroll.app.R
import com.unscroll.app.data.plus.EntitlementRepository
import com.unscroll.app.domain.plus.FreeTier
import com.unscroll.app.domain.plus.PlusProduct
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.ui.components.RowDivider
import com.unscroll.app.ui.components.SettingRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.plus.PaywallMessage
import com.unscroll.app.ui.plus.restoreMessage
import com.unscroll.app.ui.plus.textRes
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.appLabel
import com.unscroll.app.util.openUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings › Unscroll Plus: Free or Plus, the free app, manage and restore. */
data class PlusSettingsState(
    val isPlus: Boolean = false,
    /** The free user's active apps. */
    val freeApps: List<String> = emptyList(),
    /** More apps than the free tier, so there is something to choose. */
    val canChangeFreeApps: Boolean = false,
)

@HiltViewModel
class PlusSettingsViewModel @Inject constructor(
    private val entitlement: EntitlementRepository,
    trackedApps: TrackedAppsSource,
) : ViewModel() {

    val state: StateFlow<PlusSettingsState> = trackedApps.state
        .map { apps ->
            PlusSettingsState(
                isPlus = apps.isPlus,
                freeApps = apps.apps.filter { it in apps.active },
                canChangeFreeApps = !apps.isPlus && apps.apps.size > FreeTier.FREE_APPS,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlusSettingsState())

    /** Debug builds only: null follows Billing. Always null in release. */
    val debugOverride: StateFlow<Boolean?> = entitlement.debugOverride
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    private val _message = MutableStateFlow<PaywallMessage?>(null)
    val message: StateFlow<PaywallMessage?> = _message.asStateFlow()

    fun restore() {
        if (_restoring.value) return
        _restoring.value = true
        _message.value = null
        viewModelScope.launch {
            _message.value = restoreMessage(entitlement.restore())
            _restoring.value = false
        }
    }

    /** Debug: follow Billing → force Plus → force Free → follow Billing. */
    fun cycleDebugOverride() {
        val next = when (debugOverride.value) {
            null -> true
            true -> false
            false -> null
        }
        viewModelScope.launch { entitlement.setDebugOverride(next) }
    }
}

@Composable
internal fun PlusGroup(
    onOpenPlus: () -> Unit,
    onPickApps: () -> Unit,
    viewModel: PlusSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restoring by viewModel.restoring.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SettingsGroup {
        SettingRow(
            title = stringResource(R.string.plus_title),
            icon = R.drawable.ic_star,
            value = stringResource(if (state.isPlus) R.string.settings_plus_on else R.string.settings_plus_free),
            valueColor = if (state.isPlus) UnscrollTheme.status.good else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onOpenPlus,
        )
        if (state.canChangeFreeApps) {
            RowDivider()
            SettingRow(
                title = pluralStringResource(R.plurals.settings_free_apps, FreeTier.FREE_APPS, FreeTier.FREE_APPS),
                icon = R.drawable.ic_nav_apps,
                value = state.freeApps.joinToString { context.packageManager.appLabel(it) },
                onClick = onPickApps,
            )
        }
        RowDivider()
        SettingRow(
            title = stringResource(R.string.settings_plus_manage),
            icon = R.drawable.ic_layers,
            onClick = { context.openUrl(PlusProduct.manageUrl(context.packageName)) },
        )
        SettingRow(
            title = stringResource(R.string.plus_restore),
            icon = R.drawable.ic_download,
            subtitle = when {
                restoring -> stringResource(R.string.settings_plus_restoring)
                else -> message?.let { stringResource(it.textRes) }
            },
            showChevron = false,
            onClick = viewModel::restore,
        )
    }
}

/** Debug builds only: force Plus on or off to test both tiers. */
@Composable
internal fun PlusDebugRow(viewModel: PlusSettingsViewModel = hiltViewModel()) {
    val override by viewModel.debugOverride.collectAsStateWithLifecycle()
    SettingRow(
        title = stringResource(R.string.settings_debug_force_plus),
        icon = R.drawable.ic_bug,
        value = stringResource(
            when (override) {
                null -> R.string.settings_debug_force_plus_billing
                true -> R.string.settings_plus_on
                false -> R.string.settings_plus_free
            },
        ),
        showChevron = false,
        onClick = viewModel::cycleDebugOverride,
    )
}
