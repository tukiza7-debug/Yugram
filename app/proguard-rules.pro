# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================================
# TDLib (Telegram Database Library) Rules
# ============================================================

# Keep TDLib classes - they use JNI and reflection
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }

# Keep native methods
-keepclasseswithmembernames class org.drinkless.tdlib.** {
    native <methods>;
}

# Keep TDLib function and object classes used for serialization
-keep class * extends org.drinkless.tdlib.TdApi$Object { *; }
-keep class * extends org.drinkless.tdlib.TdApi$Function { *; }

# Keep TDLib constructor fields
-keepclassmembers class org.drinkless.tdlib.TdApi$** {
    public static final int CONSTRUCTOR;
}

# ============================================================
# Kotlin Coroutines Rules
# ============================================================

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================================
# Jetpack Compose Rules
# ============================================================

-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }

# Keep Compose composable functions
-keep @androidx.compose.runtime.Composable class * { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Keep Compose stability markers
-keep class androidx.compose.runtime.Stable
-keep class androidx.compose.runtime.Immutable

# ============================================================
# AndroidX Lifecycle Rules
# ============================================================

-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Lifecycle Observer methods
-keepclassmembers class * {
    @androidx.lifecycle.OnLifecycleEvent *;
}

# ============================================================
# Coil Image Loading Rules
# ============================================================

-keep class coil.** { *; }
-dontwarn coil.**

# ============================================================
# Navigation Rules
# ============================================================

-keepnames class * extends androidx.navigation.NavDestination
-keepnames class * extends androidx.navigation.Navigator

# Keep navigation arguments
-keepclassmembers class * {
    @androidx.navigation.NavType <fields>;
}

# ============================================================
# Application Model Classes
# ============================================================

# Keep our data models (used in StateFlow and serialization)
-keep class com.telegram.clone.data.model.** { *; }
-keepclassmembers class com.telegram.clone.data.model.** { *; }

# Keep core config classes
-keep class com.telegram.clone.core.config.** { *; }

# Keep BuildConfig fields
-keep class com.telegram.clone.BuildConfig { *; }

# ============================================================
# General Android Rules
# ============================================================

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ============================================================
# OkHttp / Retrofit (if used indirectly)
# ============================================================

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ============================================================
# Gson (if used)
# ============================================================

-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
