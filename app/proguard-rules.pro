# Keep app models and Compose entry points readable under R8.
-keep class no.skiltvarsler.** { *; }
-dontwarn org.sqlite.**
-dontwarn org.xerial.**
