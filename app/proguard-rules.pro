# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
#
# NOTE: rules that a dependency ships in its own jar (META-INF/proguard/*.pro
# or META-INF/com.android.tools/r8/*.pro) are merged in automatically by R8.
# Before adding a -keep for a library, check whether it already ships consumer
# rules — see app/build/outputs/mapping/release/configuration.txt, which is the
# exact merged rule set from the last release build.

# Keep GrammarFixService — referenced by name in AndroidManifest.xml
-keep class com.musa.wordwise.GrammarFixService { *; }

# Keep FixMode enum — used by name in AiClient.kt
# -keep enum com.musa.wordwise.network.FixMode { *; } (Retired in v2)


# ---------------------------------------------------------------------------
# WebView JavaScript bridge (the app's only native trust boundary)
# ---------------------------------------------------------------------------
# MainActivity.WwNativeBridge is called from the frontend as
# "WwNative.saveApiKey(...)" etc., i.e. by exact method name. R8 renames
# those methods, and the failure is silent — the JS side just starts getting
# "undefined", with no build-time warning and no crash. Keep them explicitly.
-keepclassmembers class com.musa.wordwise.MainActivity$WwNativeBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Same rule for any bridge class added later, so this stays correct even if AGP
# ever drops it from proguard-android-optimize.txt — a file we don't own.
# This mirrors AGP's default rule verbatim; duplicating it is harmless.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}


# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# The rules below are the official set from kotlinx.serialization. They are
# currently ALSO auto-merged from kotlinx-serialization-core-1.6.3
# (kotlinx-serialization-common.pro / -r8.pro), but they are repeated here so
# the requirement lives in a file this project owns, and so a future upgrade
# that drops the bundled rules cannot silently reintroduce the bug.
#
# Today no class is annotated @Serializable (all serialization goes through
# explicit serializers such as Json.parseToJsonElement), which is why this was
# never noticed: the first @Serializable class + reified Json.encodeToString(value)
# would otherwise throw at runtime on device with no build-time warning.

# Keep `Companion` object fields of serializable classes. This avoids serializer
# lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# In R8 full mode annotations are stripped from class files, so keep the
# @Serializable/@Polymorphic annotation readable — serializer<T>() inspects it
# to choose SealedClassSerializer over PolymorphicSerializer.
-if @kotlinx.serialization.Serializable class **
-keep, allowshrinking, allowoptimization, allowobfuscation, allowaccessmodification class <1>

# The -if rules above match on @kotlinx.serialization.Serializable, which is only
# present in the class file if runtime annotations survive shrinking. The
# InnerClasses attribute is what getDeclaredClasses-based companion lookup reads.
-keepattributes *Annotation*, InnerClasses, Signature

# kotlinx.serialization.internal.ClassValueReferences is referenced but never
# loaded on Android (no java.lang.ClassValue here), so the warning is expected.
-dontnote kotlinx.serialization.AnnotationsKt
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# NOTE: named companion objects still need hand-written rules (see the
# kotlinx.serialization README); none exist in this project today.


# ---------------------------------------------------------------------------
# OkHttp (4.12.0)
# ---------------------------------------------------------------------------
# okhttp-4.12.0.jar ships META-INF/proguard/okhttp3.pro, already merged by R8.
# It supplies the -dontwarn entries for Conscrypt/BouncyCastle/OpenJSSE/animal_
# sniffer. The blanket -keep below is therefore probably redundant with the
# consumer rules, but it is retained deliberately: see the Ktor note for the
# reasoning. Costs APK size, leaks nothing.
-keep class okhttp3.** { *; }
-dontwarn okio.**


# ---------------------------------------------------------------------------
# Ktor 2.3.13 (embedded CIO server serving the htmx frontend)
# ---------------------------------------------------------------------------
# ktor ships META-INF/proguard/ktor.pro, already merged by R8, which keeps the
# AtomicFU-updated volatile fields and the ServiceLoader-loaded client engines.
#
# Those consumer rules do NOT cover this app's actual usage: WordWise runs
# Ktor as a SERVER. Its routing layer resolves handlers through kotlin-reflect
# (on the runtime classpath) and Kotlin @Metadata, and only the *client* engines
# get explicit ServiceLoader keeps. Dropping the blanket -keep would obfuscate
# all of that on the strength of an assumption that could not be verified here:
# there is no device or emulator on the build machine, so a routing regression
# would only surface after the release was already published — as a blank
# window on first launch, because the UI is served by this server.
#
# Obfuscation is a size optimisation and this is a security release. An
# unverifiable runtime regression is not a trade worth making. Revisit only
# with a device smoke test to back it up.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**


# ---------------------------------------------------------------------------
# kotlinx.coroutines
# ---------------------------------------------------------------------------
# The blanket `-dontwarn kotlinx.coroutines.**` was hiding genuinely missing
# classes and has been removed. coroutines ships
# META-INF/com.android.tools/r8/coroutines.pro, merged automatically, which
# already suppresses the JVM-only classes (java.lang.instrument, sun.misc,
# java.lang.ClassValue).
# Kept because it is duplicated in coroutines.pro but stated here explicitly,
# since AtomicFieldUpdater on a mangled field fails at class-init time.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }


# Preserve stack traces in release
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Tink / errorprone annotations (referenced by EncryptedSharedPreferences)
-dontwarn com.google.errorprone.annotations.**