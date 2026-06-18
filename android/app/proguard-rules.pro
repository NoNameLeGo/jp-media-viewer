# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.jp.app.data.** { *; }

# Kotlin serialization & coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# Compose — keep runtime metadata
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# Keep SAF DocumentsContract usage
-keep class android.provider.DocumentsContract { *; }
