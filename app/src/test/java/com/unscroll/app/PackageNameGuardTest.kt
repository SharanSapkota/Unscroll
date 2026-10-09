package com.unscroll.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's ID on Google Play is com.sharansapkota.unscroll. The old ID, com.unscroll.app, is
 * still the Kotlin namespace (package of the classes and R), so it may only appear in build and
 * resource files as the `namespace` line or as a fully qualified class name
 * (com.unscroll.app.MainActivity). Anything else, like a manifest authority, a package attribute or
 * an intent action, must use the application ID (`${applicationId}` or context.packageName).
 */
class PackageNameGuardTest {

    private val root: File by lazy {
        listOf(File("."), File("..")).map { it.canonicalFile }.first { File(it, "settings.gradle.kts").exists() }
    }

    private fun checkedFiles(): List<File> {
        val app = File(root, "app")
        val gradle = listOf(
            File(root, "build.gradle.kts"),
            File(root, "settings.gradle.kts"),
            File(root, "gradle.properties"),
            File(app, "build.gradle.kts"),
            File(app, "proguard-rules.pro"),
        ) + File(root, "gradle").walkTopDown().filter { it.extension in setOf("toml", "properties") }
        val sources = File(app, "src").walkTopDown().filter { file ->
            file.isFile &&
                (file.name == "AndroidManifest.xml" || (file.extension == "xml" && file.parentFile.parentFile?.name == "res"))
        }
        return (gradle + sources).filter { it.isFile }.distinct()
    }

    @Test
    fun oldPackageName_isOnlyTheNamespaceOrAClassName() {
        val violations = checkedFiles().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val bad = OLD_ID.findAll(line).any { match -> !isAllowed(file, line, match.value) }
                if (bad) "${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
            }
        }
        assertTrue("The old package name is used as an app ID:\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun applicationId_isThePlayConsolePackage() {
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("applicationId = \"com.sharansapkota.unscroll\""))
        assertEquals(1, Regex("applicationId\\s*=").findAll(gradle).count())
    }

    @Test
    fun guard_catchesAppIdUses_andAllowsClassNames() {
        val manifest = File("AndroidManifest.xml")
        assertTrue(isAllowed(manifest, "android:settingsActivity=\"com.unscroll.app.MainActivity\"", "com.unscroll.app.MainActivity"))
        assertTrue(!isAllowed(manifest, "android:authorities=\"com.unscroll.app.provider\"", "com.unscroll.app.provider"))
        assertTrue(!isAllowed(manifest, "package=\"com.unscroll.app\"", "com.unscroll.app"))
    }

    /** The namespace line in Gradle, or a reference to a class (last segment starts upper case). */
    private fun isAllowed(file: File, line: String, match: String): Boolean {
        if (file.name == "build.gradle.kts" && line.trim().startsWith("namespace")) return true
        val last = match.substringAfterLast('.')
        return match != "com.unscroll.app" && last.first().isUpperCase()
    }

    private companion object {
        val OLD_ID = Regex("com\\.unscroll\\.app(\\.[A-Za-z_][\\w]*)*")
    }
}
