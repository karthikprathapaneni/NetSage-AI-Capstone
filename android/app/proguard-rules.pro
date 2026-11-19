# NetSage AI Proguard / R8 rules
-keepattributes *Annotation*
-keepclassmembers class * extends androidx.room.RoomDatabase
-dontwarn com.squareup.okhttp3.**
