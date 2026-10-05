# Giữ ExoPlayer + NewPipeExtractor khi minify bản release.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
