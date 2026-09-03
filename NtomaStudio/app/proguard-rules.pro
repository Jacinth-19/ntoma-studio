# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlinx coroutines
-dontwarn kotlinx.coroutines.**

# Keep model classes referenced from JSON catalog assets
-keep class com.ntoma.studio.data.remote.dto.** { *; }

# Retrofit + Gson DTOs for the optional cloud analysis client
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep class com.ntoma.studio.data.remote.dto.** { *; }
-keepclassmembers class com.ntoma.studio.data.remote.dto.** { *; }
