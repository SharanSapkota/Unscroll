package com.unscroll.app.ui.plus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.unscroll.app.R
import com.unscroll.app.data.plus.EntitlementRepository
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.plus.FreeTier
import com.unscroll.app.ui.fox.FoxMascot
import com.unscroll.app.ui.fox.LocalFoxSettings
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlusEndedViewModel @Inject constructor(
    private val entitlement: EntitlementRepository,
) : ViewModel() {
    val plusEnded: StateFlow<Boolean> = entitlement.plusEnded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismiss() {
        viewModelScope.launch { entitlement.dismissPlusEnded() }
    }
}

/** "Plus ended. 1 app stays free." Calm, one line, the fox neutral; shown until dismissed. */
@Composable
fun PlusEndedBanner(viewModel: PlusEndedViewModel = hiltViewModel()) {
    val ended by viewModel.plusEnded.collectAsStateWithLifecycle()
    if (ended) PlusEndedContent(onDismiss = viewModel::dismiss)
}

@Composable
private fun PlusEndedContent(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(start = Dimens.spaceL, end = Dimens.spaceS, top = Dimens.spaceS, bottom = Dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM),
    ) {
        if (LocalFoxSettings.current.showFox) {
            FoxMascot(mood = FoxMood.NEUTRAL, showTail = false, modifier = Modifier.size(Dimens.icon + Dimens.spaceM))
        }
        Text(
            text = pluralStringResource(R.plurals.plus_ended_banner, FreeTier.FREE_APPS, FreeTier.FREE_APPS),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_got_it)) }
    }
}

@PreviewLightDark
@Composable
private fun PlusEndedPreview() {
    UnscrollTheme {
        Surface { PlusEndedContent(onDismiss = {}) }
    }
}
