# Keep app models and Compose entry points readable under R8.
-keep class no.skiltvarsler.** { *; }
# Android Auto heads-up reads CarAppExtender from the posted notification.
-keep class androidx.car.app.** { *; }
-keep class androidx.core.app.NotificationCompat** { *; }
-dontwarn org.sqlite.**
-dontwarn org.xerial.**
