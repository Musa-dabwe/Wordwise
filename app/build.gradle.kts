
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22"
}

val keystoreProps = Properties().apply {
    val file = rootProject.file("/home/musa/Projects/keys/wordwise-release.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.musa.wordwise"
    compileSdk = 35


    defaultConfig {
        applicationId = "com.musa.wordwise"
        // Ktor's server engines need API 26+; matches PoetMusic.
        minSdk = 26
        targetSdk = 35
        // versionCode 5 keeps versionName at 1.0.0 so the v1.0.0 download URL never
        // changes, while each fix still installs over the previously published
        // build instead of being rejected as a downgrade.
        versionCode = 5
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile", ""))
            storePassword = keystoreProps.getProperty("storePassword", "")
            keyAlias = keystoreProps.getProperty("keyAlias", "")
            keyPassword = keystoreProps.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    testOptions {
        unitTests.all {
            // Robolectric fetches its android-all jar from Maven Central on first
            // use. That fetch hangs or fails with UnknownHostException on this
            // network, so run offline against the jar already cached in ~/.m2
            // (symlinked into .robolectric-deps/, gitignored).
            // To refresh: mkdir -p .robolectric-deps && ln -sf ~/.m2/repository/org/robolectric/android-all-instrumented/*/android-all-instrumented-*.jar .robolectric-deps/
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty(
                "robolectric.dependency.dir",
                rootProject.file(".robolectric-deps").absolutePath
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module"
            )
        }
    }

}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.ktor:ktor-server-core:2.3.13")
    implementation("io.ktor:ktor-server-cio:2.3.13")
    implementation("org.slf4j:slf4j-nop:2.0.13")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // Runs the embedded Ktor server's routing and guard plugin in-process, so
    // the security headers and Origin rejection are asserted rather than assumed.
    testImplementation("io.ktor:ktor-server-test-host-jvm:2.3.13")
    // Serves canned HTTP to exercise ModelCatalog.fetch without hitting
    // OpenRouter.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    // Gives SharedPreferences, the Keystore and EncryptedSharedPreferences a
    // JVM implementation so the data layer is testable off-device.
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
