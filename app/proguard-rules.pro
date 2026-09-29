# ============================================================================
# AGGRESSIVE R8 CODE OBFUSCATION & ANTI-DECOMPILATION
# ============================================================================

# Obfuscate package structures by repacking all non-kept classes into the default package
-repackageclasses ''
-allowaccessmodification
-overloadaggressively

# Strip original file names to prevent decompilers from reconstructing source file paths
-renamesourcefileattribute ""
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Strip all debug/verbose/info logs in release builds so internal strings are not exposed
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Preserve Android Components & Keep Annotations
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}
-keep @androidx.annotation.Keep class * { *; }

# ============================================================================
# ROOM DATABASE PRESERVATION
# ============================================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.covaimetertaxi.driver.data.** { *; }
-dontwarn androidx.room.paging.**

# ============================================================================
# MOSHI JSON SERIALIZATION PRESERVATION
# ============================================================================
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonClass <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter
-dontwarn com.squareup.moshi.**

# ============================================================================
# RETROFIT & OKHTTP
# ============================================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Exceptions
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class com.covaimetertaxi.driver.network.GoogleSheetsTripRequest { *; }
-keep class com.covaimetertaxi.driver.network.GoogleSheetsResponse { *; }
-keep class com.covaimetertaxi.driver.network.FirebaseDriverData { *; }

# ============================================================================
# JETPACK COMPOSE RUNTIME & ANIMATION
# ============================================================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ============================================================================
# AUDIO RESOURCES (res/raw) FOR PLAY STORE .AAB
# ============================================================================
-keepclassmembers class **.R$raw {
    public static <fields>;
}
-keep class **.R$raw { *; }

# ============================================================================
# KOTLIN COROUTINES & SYSTEM
# ============================================================================
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }


