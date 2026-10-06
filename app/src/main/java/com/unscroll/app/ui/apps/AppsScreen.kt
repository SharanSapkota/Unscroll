package com.unscroll.app.ui.apps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.unscroll.app.R
import com.unscroll.app.ui.PlaceholderScreen
import com.unscroll.app.ui.theme.UnscrollTheme

@Composable
fun AppsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        titleRes = R.string.nav_apps,
        bodyRes = R.string.apps_placeholder,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun AppsScreenPreview() {
    UnscrollTheme {
        AppsScreen()
    }
}
