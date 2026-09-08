# Proguard rules for Retrofit, Gson, and Room
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.millmitra.app.data.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class androidx.room.** { *; }
