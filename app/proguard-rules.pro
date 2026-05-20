# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Room entities and DAOs
-keep class dev.corruptedark.diditakemymeds.data.models.** { *; }
-keep class dev.corruptedark.diditakemymeds.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Keep Kotlin reflection and coroutines
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers class kotlinx.coroutines.** { *; }

# Keep Gson ser/deserialization
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep data binding
-keep class * extends androidx.databinding.ViewDataBinding { *; }
-keep class * implements androidx.databinding.ViewDataBinding { *; }

# Keep Kotpref
-keep class com.chibatching.kotpref.** { *; }

# Keep Process Phoenix
-keep class com.jakewharton.processphoenix.** { *; }
