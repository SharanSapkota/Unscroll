package com.unscroll.app.data.scroll

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.service.ScrollAccessibilityService
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * "Is the swipe counter switched on?" must follow the application ID, which differs from the
 * Kotlin namespace: Android writes "com.sharansapkota.unscroll/com.unscroll.app.service.
 * ScrollAccessibilityService" into Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ScrollServiceDetectionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val serviceClass = ScrollAccessibilityService::class.java.name

    /** A component under the new application ID, as Android would store it. */
    private val newId = ComponentName(NEW_APPLICATION_ID, serviceClass)

    @Test
    fun component_isBuiltFromContextPackageName_andTheServiceClass() {
        val component = scrollServiceComponent(context)
        assertEquals(context.packageName, component.packageName)
        assertEquals("com.unscroll.app.service.ScrollAccessibilityService", component.className)
    }

    @Test
    fun fakeComponentUnderTheNewPackage_isFoundInTheSecureSetting() {
        val enabled = "com.google.android.marvin.talkback/.TalkBackService:${newId.flattenToString()}"
        assertEquals("$NEW_APPLICATION_ID/$serviceClass", newId.flattenToString())
        assertTrue(isAccessibilityServiceEnabled(enabled, newId))
        // Android may write the list with different letter case on some builds.
        assertTrue(isAccessibilityServiceEnabled(enabled.uppercase(), newId))
    }

    @Test
    fun oldApplicationId_orShortForm_isNotOurService() {
        // The old ID (now only the namespace) is not an installed package any more.
        assertFalse(isAccessibilityServiceEnabled("$OLD_ID/$serviceClass", newId))
        // ".service.X" would resolve to com.sharansapkota.unscroll.service.X, which doesn't exist.
        assertFalse(isAccessibilityServiceEnabled("$NEW_APPLICATION_ID/.service.ScrollAccessibilityService", newId))
        assertFalse(isAccessibilityServiceEnabled("$NEW_APPLICATION_ID/com.other.Service", newId))
        assertFalse(isAccessibilityServiceEnabled(null, newId))
        assertFalse(isAccessibilityServiceEnabled("", newId))
    }

    @Test
    fun repository_readsTheSecureSetting_forItsOwnPackage_onRefresh() = runTest {
        val repository = createRepository()
        assertFalse(repository.serviceEnabled.value)

        val ours = scrollServiceComponent(context).flattenToString()
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, ours)
        repository.refresh()
        assertTrue(repository.serviceEnabled.value)

        // The same service under the old ID doesn't count.
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "$OLD_ID/$serviceClass",
        )
        repository.refresh()
        assertFalse(repository.serviceEnabled.value)
    }

    private fun TestScope.createRepository() = ScrollCountingRepository(
        context = context,
        preferences = ScrollCountingPreferences(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { tempFolder.root.resolve("test.preferences_pb") },
        ),
        clock = Clock { 0L },
        scope = backgroundScope,
        debugBuild = false,
    )

    private companion object {
        const val NEW_APPLICATION_ID = "com.sharansapkota.unscroll"
        const val OLD_ID = "com.unscroll.app"
    }
}
