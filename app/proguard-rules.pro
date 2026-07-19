# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep GrammarFixService — referenced by name in AndroidManifest.xml
-keep class com.musa.wordwise.GrammarFixService { *; }

# Keep FixMode enum — used by name in AiClient.kt
# -keep enum com.musa.wordwise.network.FixMode { *; } (Retired in v2)

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Ktor (embedded CIO server for the htmx frontend)
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Preserve stack traces in release
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
