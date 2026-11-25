# Keep rules for Tasa Bisu release builds (Android Studio APK/AAB)

# Keep Android components referenced by Manifest & WorkManager
-keep class com.example.automation.WhatsAppAutoSendService { *; }
-keep class com.example.scheduler.DailyScheduleReceiver { *; }
-keep class com.example.scheduler.DailyQuoteWorker { *; }

# Keep Room database & entities
-keep class com.example.data.local.** { *; }

# OkHttp optional platform warnings
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
