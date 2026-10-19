# Android WebView calls the login relay bridge through annotated JavaScript methods.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
