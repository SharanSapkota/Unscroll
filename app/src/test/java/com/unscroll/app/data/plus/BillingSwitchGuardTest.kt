package com.unscroll.app.data.plus

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payments have one switch: `billingEnabled` in app/build.gradle.kts, read by the app only through
 * BuildConfig.BILLING_ENABLED (PlusConfig). With it off, release builds remove Play Billing's
 * BILLING permission with the billingOff manifest (billingOffSectionOff while section blocking is
 * off too).
 */
class BillingSwitchGuardTest {

    private val root: File by lazy {
        listOf(File("."), File("..")).map { it.canonicalFile }.first { File(it, "settings.gradle.kts").exists() }
    }

    @Test
    fun theSwitchIsDefinedOnce_andFeedsBuildConfig() {
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertEquals(1, Regex("""val billingEnabled\b""").findAll(gradle).count())
        assertTrue(gradle.contains("""buildConfigField("boolean", "BILLING_ENABLED", billingEnabled.toString())"""))
        assertTrue(gradle.contains("""!billingEnabled && !sectionBlockingEnabled -> "billingOffSectionOff""""))
        assertTrue(gradle.contains("""!billingEnabled -> "billingOff""""))
        assertTrue(gradle.contains("""getByName("release").manifest.srcFile("src/${'$'}it/AndroidManifest.xml")"""))
    }

    @Test
    fun onlyPlusConfigReadsBuildConfig() {
        val readers = File(root, "app/src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.readText().contains("BuildConfig.BILLING_ENABLED") }
            .map { it.name }
            .toList()
        assertEquals(listOf("DebugBuild.kt"), readers)
    }

    @Test
    fun billingOffManifests_removeTheBillingPermission() {
        listOf("billingOff", "billingOffSectionOff").forEach { dir ->
            val manifest = File(root, "app/src/$dir/AndroidManifest.xml").readText().replace(Regex("\\s+"), " ")
            assertTrue(dir, manifest.contains("""<uses-permission android:name="com.android.vending.BILLING" tools:node="remove" />"""))
        }
    }
}
