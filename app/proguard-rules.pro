# Keep app models and Compose entry points readable under R8.
-keep class no.skiltvarsler.** { *; }
# Android Auto heads-up reads CarAppExtender / MessagingStyle from the posted notification.
-keep class androidx.car.app.** { *; }
-keep class androidx.core.app.NotificationCompat** { *; }
-keep class androidx.core.app.Person { *; }
-keep class androidx.core.app.RemoteInput { *; }
-dontwarn org.sqlite.**
-dontwarn org.xerial.**
