# Keep model classes used by Moshi
-keep class com.idp.geostampcamera.data.remote.** { *; }
-keep class com.idp.geostampcamera.domain.model.** { *; }

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel

# Room
-keep class androidx.room.** { *; }

# Moshi
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# OkHttp / Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Google play services
-keep class com.google.android.gms.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }

# Suppress warnings for CameraX
-dontwarn androidx.camera.**
