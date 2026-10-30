# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ---------------------------------------------------------------------------
# Concordance release rules.
#
# Room, Moshi, and Retrofit all rely on reflection over model types. The
# libraries ship consumer rules that cover the common cases, but these app-level
# keep rules make release builds robust against future dependency bumps: they
# pin the entities and serialized models so shrinking/obfuscation can never
# strip a field or rename a property that a query or a JSON codec depends on.
# ---------------------------------------------------------------------------

# Line numbers in crash reports (developer-facing, not a functional rule).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Moshi / Retrofit reflective JSON: keep annotated models and their accessors.
-keep class com.example.concordance.model.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.Json <methods>;
}

# Keep any @Json/@JsonClass-annotated type and its members (defensive: covers
# models added outside the model package).
-keepclasseswithmembers,includedescriptorclasses class * {
    @com.squareup.moshi.Json <fields>;
}

# Retrofit: keep service interfaces and their annotated methods.
-keep,allowobfuscation,allowshrinking interface * { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp / Okio optional dependencies that are only loaded reflectively.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Room generates schema/DAO code at compile time; entities are reflected on by
# the generated adapters, so keep the entity classes and their fields whole.
-keep class com.example.concordance.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
