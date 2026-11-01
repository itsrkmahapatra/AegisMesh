# AegisMesh Proguard / R8 Optimization Rules

# MapLibre Native SDK
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# Google Play Services Nearby Connections
-keep class com.google.android.gms.nearby.** { *; }
-dontwarn com.google.android.gms.nearby.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep data models
-keepclassmembers class com.itsrkmahapatra.aegismesh.domain.model.** { *; }
-keepclassmembers class com.itsrkmahapatra.aegismesh.data.local.entity.** { *; }

