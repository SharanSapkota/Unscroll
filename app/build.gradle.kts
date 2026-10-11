import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

/**
 * Release signing comes from keystore.properties at the repo root (gitignored, never committed):
 * storeFile, storePassword, keyAlias, keyPassword. Debug builds don't need it; release builds fail
 * with a clear message without it (see docs/RELEASE.md).
 */
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val missingSigningKeys: List<String> = signingKeys.filter { keystoreProperties.getProperty(it).isNullOrBlank() }

/**
 * THE master switch for payments (Unscroll Plus, Google Play Billing). Off for now: everything is
 * unlocked for everyone, no BillingClient is created, the paywall and every Plus row are hidden,
 * and release builds drop the com.android.vending.BILLING permission. The billing code stays in
 * the repo; to turn payments back on, change `false` to `true` here (docs/RELEASE.md). For a
 * one-off build, `-Punscroll.billingEnabled=true` overrides it. The app reads it only through
 * `BuildConfig.BILLING_ENABLED` (PlusConfig).
 */
val billingEnabled: Boolean = providers.gradleProperty("unscroll.billingEnabled").orNull?.toBooleanStrict() ?: false

android {
    // The Kotlin package and the R class keep this namespace; only the installed app ID (below) changed.
    namespace = "com.unscroll.app"
    compileSdk = 36

    defaultConfig {
        // The package name registered in Google Play Console. Never change it after the first upload.
        applicationId = "com.sharansapkota.unscroll"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Payments on or off, from billingEnabled above (the only place to change it).
        buildConfigField("boolean", "BILLING_ENABLED", billingEnabled.toString())
    }

    sourceSets {
        // With payments off, release builds remove the BILLING permission that Play Billing adds.
        // Debug builds keep the library's manifest as it is.
        if (!billingEnabled) getByName("release").manifest.srcFile("src/billingOff/AndroidManifest.xml")
    }

    signingConfigs {
        if (missingSigningKeys.isEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates debug-only tools such as sample data; BILLING_ENABLED gates payments.
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
    }
}

// Release packaging needs keystore.properties; fail early with a clear message instead of an
// unsigned or half-configured build.
val checkReleaseSigning by tasks.registering {
    val file = keystorePropertiesFile
    val missing = missingSigningKeys
    doFirst {
        if (missing.isNotEmpty()) {
            val reason = if (!file.exists()) "${file.path} is missing" else "${file.path} lacks ${missing.joinToString()}"
            throw GradleException(
                "Release signing is not configured: $reason. Create keystore.properties at the repo root " +
                    "with storeFile, storePassword, keyAlias and keyPassword (it is gitignored; see docs/RELEASE.md).",
            )
        }
    }
}
tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "packageRelease", "packageReleaseBundle") }
    .configureEach { dependsOn(checkReleaseSigning) }

room {
    // Room schema history, needed to write and test migrations later. Commit these files.
    // The Room Gradle plugin gives each variant its own output and copies the result here, so
    // parallel debug/release KSP tasks can't read each other's half-written schema files.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // The launch splash with the fox on every Android version (SplashScreen API backport).
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    // Unscroll Plus: Google Play Billing is the only payment system (no accounts, no backend).
    // Kept while payments are off (billingEnabled = false): the code stays, no client is created.
    implementation(libs.play.billing)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
