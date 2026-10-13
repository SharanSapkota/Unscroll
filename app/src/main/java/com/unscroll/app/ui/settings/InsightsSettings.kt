package com.unscroll.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.unscroll.app.R
import com.unscroll.app.data.insights.InsightPreferences
import com.unscroll.app.data.insights.InsightSettings
import com.unscroll.app.domain.insights.InsightTone
import com.unscroll.app.ui.components.RowDivider
import com.unscroll.app.ui.components.SegmentedControl
import com.unscroll.app.ui.components.SettingRow
import com.unscroll.app.ui.components.SettingSwitchRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.components.UnscrollSheet
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class InsightSettingsViewModel @Inject constructor(
    private val preferences: InsightPreferences,
) : ViewModel() {

    val settings: StateFlow<InsightSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightSettings())

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setEnabled(enabled) }
    }

    fun setTone(tone: InsightTone) {
        viewModelScope.launch { preferences.setTone(tone) }
    }

    fun setAge(age: Int?) {
        viewModelScope.launch { preferences.setAge(age) }
    }

    fun setWeeklyNotification(enabled: Boolean) {
        viewModelScope.launch { preferences.setWeeklyNotification(enabled) }
    }
}

/** Settings › Insights: on/off, tone, the optional age for the projection, the weekly notification. */
@Composable
internal fun InsightsGroup(viewModel: InsightSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    InsightsGroupContent(
        settings = settings,
        onEnabled = viewModel::setEnabled,
        onTone = viewModel::setTone,
        onAge = viewModel::setAge,
        onWeekly = viewModel::setWeeklyNotification,
    )
}

@Composable
private fun InsightsGroupContent(
    settings: InsightSettings,
    onEnabled: (Boolean) -> Unit,
    onTone: (InsightTone) -> Unit,
    onAge: (Int?) -> Unit,
    onWeekly: (Boolean) -> Unit,
) {
    var ageOpen by rememberSaveable { mutableStateOf(false) }
    SettingsGroup {
        SettingSwitchRow(
            title = stringResource(R.string.settings_insights),
            icon = R.drawable.ic_star,
            checked = settings.enabled,
            onCheckedChange = onEnabled,
        )
        if (settings.enabled) {
            RowDivider()
            Column(
                modifier = Modifier.padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
            ) {
                Text(text = stringResource(R.string.settings_insights_tone), style = MaterialTheme.typography.bodyLarge)
                SegmentedControl(
                    options = InsightTone.entries,
                    selected = settings.tone,
                    label = { stringResource(it.labelRes) },
                    onSelect = onTone,
                )
                if (settings.tone == InsightTone.BRUTAL) {
                    Text(
                        text = stringResource(R.string.settings_insights_brutal_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingRow(
                title = stringResource(R.string.settings_insights_age),
                icon = R.drawable.ic_hourglass,
                value = settings.age?.toString() ?: stringResource(R.string.settings_insights_age_unset),
                onClick = { ageOpen = true },
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_insights_weekly),
                icon = R.drawable.ic_bell,
                checked = settings.weeklyNotification,
                onCheckedChange = onWeekly,
            )
        }
    }
    if (ageOpen) {
        AgeSheet(
            age = settings.age,
            onSave = {
                onAge(it)
                ageOpen = false
            },
            onDismiss = { ageOpen = false },
        )
    }
}

/** A number field for the age; empty or "Clear" removes it (then the projection looks 50 years ahead). */
@Composable
private fun AgeSheet(age: Int?, onSave: (Int?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(age?.toString().orEmpty()) }
    val parsed = text.toIntOrNull()
    val valid = text.isEmpty() || parsed in InsightSettings.AGE_RANGE
    UnscrollSheet(title = stringResource(R.string.settings_insights_age), onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.settings_insights_age_info),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { value -> text = value.filter { it.isDigit() }.take(AGE_DIGITS) },
            label = { Text(stringResource(R.string.settings_insights_age_hint)) },
            singleLine = true,
            isError = !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (valid) onSave(parsed) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
            TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.settings_insights_age_clear)) }
            Button(onClick = { onSave(parsed) }, enabled = valid) { Text(stringResource(R.string.settings_insights_age_save)) }
        }
    }
}

private const val AGE_DIGITS = 2

private val InsightTone.labelRes: Int
    get() = when (this) {
        InsightTone.GENTLE -> R.string.insights_tone_gentle
        InsightTone.HONEST -> R.string.insights_tone_honest
        InsightTone.BRUTAL -> R.string.insights_tone_brutal
    }

@PreviewLightDark
@Composable
private fun InsightsGroupPreview() {
    UnscrollTheme {
        Surface {
            Column(modifier = Modifier.padding(Dimens.screenPadding)) {
                InsightsGroupContent(
                    settings = InsightSettings(tone = InsightTone.BRUTAL, age = 34),
                    onEnabled = {},
                    onTone = {},
                    onAge = {},
                    onWeekly = {},
                )
            }
        }
    }
}
