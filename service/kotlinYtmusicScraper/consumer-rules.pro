# Shared by every app consuming this library, including the minified Wear target.
# NewPipe looks up localized date patterns and Rhino Java members dynamically.
# See https://github.com/TeamNewPipe/NewPipe/blob/dev/app/proguard-rules.pro
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.ocpsoft.prettytime.i18n.Resources* { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
# NewPipe explicitly uses interpreted mode. These optional desktop/JIT APIs are unavailable
# on Android; rhino-engine (javax.script) is excluded from the dependency itself.
-dontwarn jdk.dynalink.**
-dontwarn org.mozilla.javascript.tools.**
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn java.beans.**

# Protobuf's generated message schema refers to field names at runtime.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# The vendored provider does not publish its own consumer rules. Preserve its Gson models,
# Retrofit service interfaces and native V8 bindings as in its release configuration.
-keep class com.liskovsoft.** { *; }
-keep interface com.liskovsoft.** { *; }
-keep class com.eclipsesource.v8.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# WebView invokes these methods by their original names in phone token generation.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
