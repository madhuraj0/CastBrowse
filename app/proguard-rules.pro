# Standard annotations and attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Keep WebViews JavaScript Interface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Kotlinx Serialization generated serializers and annotated classes
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class kotlinx.serialization.json.** { *; }

# Keep OkHttp & Okio internals
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Suppress missing class warnings for Tink's unused KeysDownloader (referencing Google API Client and Joda Time)
-dontwarn com.google.crypto.tink.util.KeysDownloader
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**
