# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep GrammarFixService — referenced by name in AndroidManifest.xml
-keep class com.musa.wordwise.GrammarFixService { *; }

# Keep FixMode enum — used by name in AiClient.kt
-keep enum com.musa.wordwise.network.FixMode { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Preserve stack traces in release
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
