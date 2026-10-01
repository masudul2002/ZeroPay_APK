# ==============================================================================
# ZeroPay Production ProGuard / R8 Optimization & Obfuscation Rules
# ==============================================================================

# 1. Android Core Components (Activities, Application, Services, Receivers)
-keep public class com.example.ZeroPayApp { *; }
-keep public class com.example.MainActivity { *; }
-keep public class com.example.receiver.SmsReceiver { *; }
-keep public class com.example.service.SmsForwarderService { *; }
-keep class * extends android.app.Activity
-keep class * extends android.app.Application
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver

# Preserve BroadcastReceiver and Telephony callbacks
-keepclassmembers class * extends android.content.BroadcastReceiver {
    public void onReceive(android.content.Context, android.content.Intent);
}

# 2. ZeroPay Data Models, Config & Payloads (Preserve JSON Reflection & Serialization)
-keep class com.example.data.model.** { *; }
-keep class com.example.util.ExtractedTransactionData { *; }
-keep class com.example.util.FilterEvaluation** { *; }
-keep class com.example.util.ProviderParsingRule { *; }
-keep class com.example.util.ParsingGroupMapping { *; }
-keep class com.example.util.SmsParsingConfig { *; }
-keep class com.example.util.SmsFilterAndParser { *; }
-keep class com.example.data.network.DispatchResult** { *; }

# 3. Room Database, Entities and DAOs
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-keep class com.example.data.local.** { *; }

# 4. AndroidX Security & EncryptedSharedPreferences (AES256 GCM/SIV)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# 5. OkHttp3 & Okio Network Transport
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# 6. ZXing & CameraX (QR Code Scanner)
-keep class com.google.zxing.** { *; }
-keep interface com.google.zxing.** { *; }
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# 7. Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# 8. Keep line numbers for production crash reports / debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
