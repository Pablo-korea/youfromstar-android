# ✅ WebView JS 인터페이스 메서드 보존 (범용)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclasseswithmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ✅ 어노테이션/내부클래스 메타데이터 보존
-keepattributes *Annotation*, InnerClasses, EnclosingMethod

# ✅ 웹뷰 관련 클래스 이름 유지 (보수적 권장)
-keep class * extends android.app.Activity { *; }
-keep class * extends android.webkit.WebViewClient { *; }
-keep class * extends android.webkit.WebChromeClient { *; }

# ✅ 우리 브릿지 클래스 통째 보존 (패키지 경로 확인!)
-keep class com.example.app.MainActivity$AndroidTTSBridge { *; }
-keep class com.example.app.WebAppInterface { *; }
