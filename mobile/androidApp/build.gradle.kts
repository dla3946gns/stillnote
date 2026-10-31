plugins {
    alias(libs.plugins.google.services)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The Play upload keystore must never be committed to the repository.
 * Values are supplied by the developer's shell or a manually triggered,
 * main-only GitHub Actions release job.
 */
val uploadKeystore = providers.environmentVariable("STILLNOTE_UPLOAD_KEYSTORE").orNull
val uploadStorePassword = providers.environmentVariable("STILLNOTE_UPLOAD_STORE_PASSWORD").orNull
val uploadKeyAlias = providers.environmentVariable("STILLNOTE_UPLOAD_KEY_ALIAS").orNull
val uploadKeyPassword = providers.environmentVariable("STILLNOTE_UPLOAD_KEY_PASSWORD").orNull
val uploadValues = listOf(uploadKeystore, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
val releaseSigningConfigured = uploadValues.all { !it.isNullOrBlank() }
if (uploadValues.any { !it.isNullOrBlank() } && !releaseSigningConfigured) {
    throw GradleException(
        "Incomplete Android upload signing configuration: set all four STILLNOTE_UPLOAD_* variables."
    )
}

val stillnoteVersionCode = providers.gradleProperty("stillnoteVersionCode").orNull?.toIntOrNull()
    ?: throw GradleException("Set an integer stillnoteVersionCode in mobile/gradle.properties.")
require(stillnoteVersionCode in 1..2_100_000_000) {
    "stillnoteVersionCode must be a positive value within Google Play's limit."
}
val stillnoteVersionName = providers.gradleProperty("stillnoteVersionName").orNull
    ?: throw GradleException("Set stillnoteVersionName in mobile/gradle.properties.")
require(stillnoteVersionName.isNotBlank()) { "stillnoteVersionName must not be blank." }

android {
    namespace = "com.stillnotes.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.stillnotes.app"
        minSdk = 26
        targetSdk = 37
        versionCode = stillnoteVersionCode
        versionName = stillnoteVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (releaseSigningConfigured) {
        signingConfigs.create("playUpload") {
            storeFile = file(uploadKeystore!!)
            storePassword = uploadStorePassword!!
            keyAlias = uploadKeyAlias!!
            keyPassword = uploadKeyPassword!!
        }
    }
    buildTypes.getByName("release") {
        isDebuggable = false
        if (releaseSigningConfigured) {
            signingConfig = signingConfigs.getByName("playUpload")
        }
    }

    buildFeatures.compose = true
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.12.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.12.1")
}

/**
 * Never produce a release bundle with an unsigned or debug-key identity.
 * PR CI uses a throwaway local signing key and discards its AAB.
 */
tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Require a usable non-repository upload keystore for Play AAB builds."
    doLast {
        check(releaseSigningConfigured) {
            "Release signing is missing. Set STILLNOTE_UPLOAD_KEYSTORE, " +
                "STILLNOTE_UPLOAD_STORE_PASSWORD, STILLNOTE_UPLOAD_KEY_ALIAS and " +
                "STILLNOTE_UPLOAD_KEY_PASSWORD. Do not use a debug key for Play releases."
        }
        val uploadKeyFile = file(uploadKeystore!!)
        check(uploadKeyFile.isFile && uploadKeyFile.length() > 0L) {
            "The configured upload keystore path does not point to a non-empty file."
        }
    }
}

// Attach the verification even if the task is registered lazily by AGP.
tasks.matching { it.name == "bundleRelease" }.configureEach {
    dependsOn("verifyReleaseSigning")
}
