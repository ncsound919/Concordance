# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep line numbers in release stack traces. Without them a field crash is a
# list of obfuscated frames; the source file name is rewritten so the original
# path is not exposed.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp probes for optional TLS providers at runtime and falls back to the
# platform one when they are absent. They are not on the classpath here, so R8
# reports them as missing classes and refuses to finish the release build.
# Without these rules `:app:bundleRelease` fails at `minifyReleaseWithR8`
# with "Missing class org.conscrypt..." and friends.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Compile-time-only annotations (Guava/Error Prone). They carry no runtime code.
-dontwarn com.google.errorprone.annotations.**

# PDFBox's optional JPEG-2000 decoder. It is only referenced from JPXFilter;
# the app never opens JPX images, and the decoder is not a dependency.
-dontwarn com.gemalto.jp2.**
