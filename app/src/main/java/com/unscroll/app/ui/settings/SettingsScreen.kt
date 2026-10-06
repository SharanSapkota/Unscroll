package com.unscroll.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.unscroll.app.R
import com.unscroll.app.ui.PlaceholderScreen
import com.unscroll.app.ui.theme.UnscrollTheme

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        titleRes = R.string.nav_settings,
        bodyRes = R.string.settings_placeholder,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    UnscrollTheme {
        SettingsScreen()
    }
}
