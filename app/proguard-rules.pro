# DeepFocus ProGuard Rules

# Keep accessibility service
-keep class com.deepfocus.app.service.accessibility.DeepFocusAccessibilityService { *; }

# Keep device admin receiver
-keep class com.deepfocus.app.service.device_admin.DeepFocusDeviceAdmin { *; }

# Keep boot receiver
-keep class com.deepfocus.app.service.blocking.BootReceiver { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ComponentSupplier { *; }

# Compose
-dontwarn androidx.compose.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
