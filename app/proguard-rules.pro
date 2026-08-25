# ProGuard / R8 rules for Outpost.
#
# Release builds currently set isMinifyEnabled = false, so these rules are not yet
# applied. They are kept correct and ready so that enabling shrinking is a one-line
# change rather than a debugging session.
#
# The awkward dependencies here are SSHJ and BouncyCastle: both resolve algorithm
# implementations reflectively by class name, so R8 cannot see the references and
# will happily strip ciphers the app needs at runtime. A stripped cipher does not
# fail at build time - it fails as a key-exchange error against a real server.

# ---------------------------------------------------------------------------
# SSHJ
# ---------------------------------------------------------------------------
-keep class net.schmizz.sshj.** { *; }
-keep interface net.schmizz.sshj.** { *; }
-dontwarn net.schmizz.sshj.**

# SSHJ discovers key exchange, cipher, MAC and signature factories through
# named inner Factory classes. Removing them breaks the handshake.
-keep class net.schmizz.sshj.transport.** { *; }
-keep class net.schmizz.sshj.userauth.** { *; }
-keep class net.schmizz.sshj.signature.** { *; }

# Optional SSHJ dependencies that are absent on Android.
-dontwarn com.hierynomus.**
-dontwarn org.slf4j.**
-dontwarn java.awt.**
-dontwarn javax.swing.**

# ---------------------------------------------------------------------------
# BouncyCastle
# ---------------------------------------------------------------------------
# The JCE provider registers algorithms by reflective class lookup.
-keep class org.bouncycastle.** { *; }
-keep interface org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Entities are read and written reflectively by generated code.
-keepclassmembers class io.github.ameralkhorasani.outpost.data.model.** { *; }

# ---------------------------------------------------------------------------
# Hilt / Dagger
# ---------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-dontwarn dagger.hilt.**

# ---------------------------------------------------------------------------
# Kotlin / Coroutines
# ---------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
-keep class kotlin.Metadata { *; }

# ---------------------------------------------------------------------------
# The JavaScript bridge into xterm.js
# ---------------------------------------------------------------------------
# @JavascriptInterface methods are invoked by name from JavaScript, so R8 has no
# reference to follow and would rename them.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------------------------------------------------------------------------
# Crash reports should stay readable
# ---------------------------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes *Annotation*
-renamesourcefileattribute SourceFile
