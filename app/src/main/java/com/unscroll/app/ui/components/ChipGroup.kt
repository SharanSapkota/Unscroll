package com.unscroll.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.unscroll.app.ui.theme.Dimens

/**
 * One horizontally scrolling row of choice chips, with a haptic tick on each tap. It scrolls
 * rather than clips, so large font sizes keep every chip whole.
 */
@Composable
fun <T> ChipGroup(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dimens.spaceL),
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = isSelected(option),
                enabled = enabled,
                onClick = {
                    haptics.tick()
                    onSelect(option)
                },
                label = { Text(label(option), style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}
