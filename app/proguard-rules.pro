# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep EmulatorBridge and all its methods (JNI)
-keep class com.vsmileemu.android.native_bridge.** { *; }

# Keep data classes used for serialization
-keep class com.vsmileemu.android.data.model.** { *; }

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }








