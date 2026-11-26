# Keep kotlinx.serialization DTOs for server version response
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class com.edurda77.download_install.downloader.remote.** {
    *;
}
-keep,includedescriptorclasses class com.edurda77.download_install.downloader.remote.**$$serializer {
    *;
}

# Keep WorkManager Workers
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep notification action BroadcastReceiver
-keep class com.edurda77.download_install.receiver.UpdateActionReceiver {
    public <init>();
}
