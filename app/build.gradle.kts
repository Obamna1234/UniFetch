@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.FilterConfiguration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.room)
    alias(libs.plugins.ktfmt.gradle)
}

// Release signing. The keystore and passwords come from environment variables that the GitHub
// release workflow fills in from GitHub Secrets. They are never stored in the repository.
val signingKeystorePath: String? =
    System.getenv("UNIFETCH_KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }

val splitApks = !project.hasProperty("noSplits")

val abiFilterList = (properties["ABI_FILTERS"] as String).split(';')

val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)

// Release builds pass the git tag (e.g. -PappVersion=v1.2.0); other builds use currentVersion.
val appVersion =
    (findProperty("appVersion") as String?)?.takeIf { it.isNotBlank() }?.let(::versionFromTag)
        ?: currentVersion
val baseVersionName = appVersion.name
val currentVersionCode = appVersion.code.toInt()

android {
    compileSdk = 35

    if (signingKeystorePath != null) {
        signingConfigs {
            create("unifetch") {
                storeFile = file(signingKeystorePath)
                storePassword = System.getenv("UNIFETCH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UNIFETCH_KEY_ALIAS")
                keyPassword = System.getenv("UNIFETCH_KEY_PASSWORD")
            }
        }
    }

    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "com.unifetch.app"
        minSdk = 24
        targetSdk = 35
        versionCode = currentVersionCode

        versionName = baseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        if (splitApks) {
            splits {
                abi {
                    isEnable = true
                    reset()
                    include("arm64-v8a", "armeabi-v7a", "x86_64")
                    isUniversalApk = true
                }
            }
        } else {
            ndk { abiFilters.addAll(abiFilterList) }
        }
    }

    room { schemaDirectory("$projectDir/schemas") }
    ksp { arg("room.incremental", "true") }

    androidComponents {
        onVariants { variant ->
            variant.outputs.forEach { output ->
                val name =
                    if (splitApks) {
                        output.filters
                            .find { it.filterType == FilterConfiguration.FilterType.ABI }
                            ?.identifier
                    } else {
                        abiFilterList.firstOrNull()
                    }

                val baseAbiCode = abiCodes[name]

                if (baseAbiCode != null) {
                    output.versionCode.set(baseAbiCode + (output.versionCode.get() ?: 0))
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signingKeystorePath != null) {
                signingConfig = signingConfigs.getByName("unifetch")
            }
        }
        debug {
            // Once the signing key is set up, test builds use it too, so a newer test build can
            // be installed over an older one without uninstalling first.
            if (signingKeystorePath != null) {
                signingConfig = signingConfigs.getByName("unifetch")
            }
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "UniFetch Debug")
        }
    }

    flavorDimensions += "publishChannel"

    productFlavors {
        create("generic") {
            dimension = "publishChannel"
            isDefault = true
        }

        create("githubPreview") {
            dimension = "publishChannel"
            applicationIdSuffix = ".preview"
            resValue("string", "app_name", "UniFetch Preview")
        }

        create("fdroid") {
            dimension = "publishChannel"
            versionName = "$baseVersionName-(F-Droid)"
        }
    }

    lint { disable.addAll(listOf("MissingTranslation", "ExtraTranslation", "MissingQuantity")) }

    // APK names look like UniFetch-1.2.0-arm64-v8a.apk or UniFetch-1.2.0-universal.apk.
    // The in-app updater picks the right file by looking for the phone's ABI in the name.
    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abi = output.getFilter("ABI") ?: "universal"
            output.outputFileName = "UniFetch-${variant.versionName}-$abi.apk"
        }
    }

    kotlinOptions { freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs.useLegacyPackaging = true
    }
    androidResources { generateLocaleConfig = true }

    namespace = "com.unifetch.app"
}

ktfmt { kotlinLangStyle() }

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":color"))

    implementation(libs.bundles.core)

    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidxCompose)
    implementation(libs.bundles.accompanist)

    implementation(libs.coil.kt.compose)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)

    implementation(libs.bundles.youtubedlAndroid)

    implementation(libs.mmkv)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.compose.ui.tooling)
}
