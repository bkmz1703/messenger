# Keep Firebase model classes used reflectively by Firestore deserialization.
-keepclassmembers class ru.ilyakirollov.messenger.data.model.** {
    <init>();
    *;
}

# Hilt
-keep class dagger.hilt.android.internal.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp

# Coroutines
-dontwarn kotlinx.coroutines.**

# WebRTC
-keep class org.webrtc.** { *; }
-keep class io.getstream.webrtc.** { *; }
