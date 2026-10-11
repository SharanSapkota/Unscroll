package com.unscroll.app.data.section

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Section blocking has one switch: `sectionBlockingEnabled` in app/build.gradle.kts, read by the
 * app only through BuildConfig.SECTION_BLOCKING_ENABLED (SectionBlockingConfig). With it off, the
 * section service (the only one with canRetrieveWindowContent) is removed from the merged
 * manifest, and scroll counting's service never gets window content either way.
 */
class SectionBlockingSwitchGuardTest {

    private val root: File by lazy {
        listOf(File("."), File("..")).map { it.canonicalFile }.first { File(it, "settings.gradle.kts").exists() }
    }

    private fun read(path: String) = File(root, path).readText().replace(Regex("\\s+"), " ")

    @Test
    fun theSwitchIsDefinedOnce_andFeedsBuildConfig() {
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertEquals(1, Regex("""val sectionBlockingEnabled\b""").findAll(gradle).count())
        assertTrue(gradle.contains("""buildConfigField("boolean", "SECTION_BLOCKING_ENABLED", sectionBlockingEnabled.toString())"""))
        assertTrue(gradle.contains("""!sectionBlockingEnabled -> "sectionOff""""))
        assertTrue(gradle.contains("""getByName("debug").manifest.srcFile("src/sectionOff/AndroidManifest.xml")"""))
    }

    @Test
    fun onlySectionBlockingConfigReadsBuildConfig() {
        val readers = File(root, "app/src/main").walkTopDown()
            .filter {
                it.isFile && it.extension == "kt" &&
                    (it.readText().contains("BuildConfig.SECTION_BLOCKING_ENABLED") || it.readText().contains("BuildConfig.SECTION_INSPECTOR_FLAG"))
            }
            .map { it.name }
            .toList()
        assertEquals(listOf("SectionBlockingConfig.kt"), readers)
    }

    @Test
    fun sectionOffManifests_removeTheSectionService() {
        val removal = """<service android:name="com.unscroll.app.service.SectionBlockingService" tools:node="remove" />"""
        listOf("sectionOff", "billingOffSectionOff").forEach { dir ->
            assertTrue(dir, read("app/src/$dir/AndroidManifest.xml").contains(removal))
        }
        // The main manifest still declares it (for when the switch is on), by the same class.
        assertTrue(read("app/src/main/AndroidManifest.xml").contains("""android:name=".service.SectionBlockingService""""))
    }

    @Test
    fun scrollCountingService_neverGetsWindowContent() {
        val config = read("app/src/main/res/xml/accessibility_service_config.xml")
        assertTrue(config.contains("""android:canRetrieveWindowContent="false""""))
        assertTrue(config.contains("""android:accessibilityEventTypes="typeViewScrolled|typeWindowStateChanged""""))
        assertTrue(!config.contains("flagReportViewIds") && !config.contains("flagRetrieveInteractiveWindows"))
    }
}
