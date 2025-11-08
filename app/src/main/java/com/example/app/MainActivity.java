package com.example.app;

import com.example.app.BillingHelper;
import com.example.app.WebAppInterface;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.MailTo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;                  // ✅ 추가
import android.os.Looper;                 // ✅ 추가
import android.speech.tts.TextToSpeech;   // ✅ TTS
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface; // ✅ JS 브릿지 어노테이션
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebView.WebViewTransport;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.Keep;           // ✅ (권장) R8 보존 힌트
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import androidx.core.view.ViewCompat;    // insets 유틸
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;                  // 언어 태그용
import android.content.res.Configuration;
import androidx.annotation.NonNull;
import android.view.ViewGroup;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private WebView myWebView;
    private static final String HOME_URL = "https://youfromstar.odha.com/";
    private long backPressedTime = 0;
    private static final int BACK_INTERVAL = 2000;

    private BillingHelper billing;

    private static final int MAX_WIDTH_DP = 420; // 갤럭시 S 울트라급

    // ✅ 네이티브 TTS
    private TextToSpeech tts;
    private float ttsRate = 1.0f;
    private float ttsPitch = 1.0f;
    private volatile boolean ttsReady = false;                 // ✅ 준비 플래그
    private final Handler mainHandler = new Handler(Looper.getMainLooper()); // ✅ 재시도용

    private void applyWebViewWidthLimit() {
        if (myWebView == null) return;
        View parent = (View) myWebView.getParent();
        if (parent == null) return;

        parent.post(() -> {
            float density = getResources().getDisplayMetrics().density;
            int maxPx = Math.round(MAX_WIDTH_DP * density);
            int parentW = parent.getWidth();
            if (parentW == 0) return;

            int target = Math.min(parentW, maxPx);

            ViewGroup.LayoutParams lp = myWebView.getLayoutParams();
            lp.width = target;                // ✅ 최대폭 제한
            myWebView.setLayoutParams(lp);
            // 높이는 match_parent 그대로
        });
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 하드웨어 가속
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        // 시스템바 영역 분리
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        // 남아있을 수 있는 플래그 제거
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

        // 시스템바 보이도록
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(decor);
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        }

        setContentView(R.layout.activity_main);
        myWebView = findViewById(R.id.webview);

        applyWebViewWidthLimit();   // ✅ 추가

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // WebView 설정
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        myWebView.clearCache(true);
        myWebView.clearHistory();

        // 인셋 패딩
        ViewCompat.setOnApplyWindowInsetsListener(myWebView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        // WebViewClient
        myWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return MainActivity.this.handleUrlOverride(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        ? request.getUrl().toString()
                        : null;
                return MainActivity.this.handleUrlOverride(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // 필요 시만 사용
            }
        });

        // 팝업 처리
        myWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView temp = new WebView(MainActivity.this);
                temp.getSettings().setJavaScriptEnabled(true);
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView v, String url, Bitmap favicon) {
                        if (url != null) {
                            boolean handled = MainActivity.this.handleUrlOverride(url);
                            if (!handled) myWebView.loadUrl(url);
                        }
                        try { v.stopLoading(); } catch (Exception ignored) {}
                        try { v.destroy(); } catch (Exception ignored) {}
                    }
                });

                WebViewTransport transport = (WebViewTransport) resultMsg.obj;
                transport.setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                try { window.destroy(); } catch (Exception ignored) {}
            }
        });

        // ✅ TTS 초기화
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.KOREAN);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Korean TTS not supported or missing data");
                    // (선택) 언어 데이터 설치 유도
                    try {
                        startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                    } catch (Exception ignored) {}
                }
                tts.setSpeechRate(ttsRate);
                tts.setPitch(ttsPitch);
                ttsReady = true;                                   // ✅ 준비 완료!
                Log.d(TAG, "TTS ready");
            } else {
                Log.e(TAG, "TTS init failed: " + status);
            }
        });

        // Billing
        billing = new BillingHelper(this, myWebView);
        billing.start();
        myWebView.addJavascriptInterface(new WebAppInterface(this, billing), "AndroidBilling");

        // ✅ JS → 네이티브 TTS 브릿지 (내부클래스 사용)
        myWebView.addJavascriptInterface(new AndroidTTSBridge(), "AndroidTTS");

        // 시스템 바 색
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(0xFFFFFFFF);
            getWindow().setNavigationBarColor(0xFF000000);
        }
        WindowInsetsControllerCompat c = ViewCompat.getWindowInsetsController(decor);
        if (c != null) {
            c.setAppearanceLightStatusBars(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.setAppearanceLightStatusBars(true);
                c.setAppearanceLightNavigationBars(false);
            }
        }

        // 첫 페이지 로드
        myWebView.loadUrl(HOME_URL);
    }

    // ======= ✅ JS → 네이티브 TTS 브릿지 =======
    @Keep // ✅ (권장) R8이 내부클래스/이름을 유지하도록 힌트
    public class AndroidTTSBridge {

        @JavascriptInterface @Keep
        public String ping() {                 // ✅ 진단용: 설치본에서 콘솔로 확인 가능
            return "pong";
        }

        @JavascriptInterface @Keep
        public void readText(final String text, final String lang, final String rateStr, final String pitchStr) {
            if (text == null || text.trim().isEmpty() || tts == null) return;

            // ✅ TTS가 아직 준비 전이면 잠시 후 재시도 (설치 직후/첫 실행 케이스 방지)
            if (!ttsReady) {
                Log.d(TAG, "TTS not ready yet. retry in 300ms");
                mainHandler.postDelayed(() -> readText(text, lang, rateStr, pitchStr), 300);
                return;
            }

            try {
                float rate = parseFloatSafe(rateStr, 1.0f);
                float pitch = parseFloatSafe(pitchStr, 1.0f);
                ttsRate = clamp(rate, 0.5f, 2.0f);
                ttsPitch = clamp(pitch, 0.5f, 2.0f);

                if (lang != null && !lang.isEmpty()) {
                    try {
                        int r = tts.setLanguage(Locale.forLanguageTag(lang)); // "ko-KR" 등
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "Lang not supported: " + lang);
                        }
                    } catch (Exception ignored) {}
                }
                tts.setSpeechRate(ttsRate);
                tts.setPitch(ttsPitch);

                tts.stop(); // 중복 방지
                int res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "youfromstar-tts");
                Log.d(TAG, "tts.speak result=" + res);
            } catch (Exception e) {
                Log.e(TAG, "AndroidTTS.readText error", e);
            }
        }

        @JavascriptInterface @Keep
        public void stop() {
            try { if (tts != null) tts.stop(); } catch (Exception ignored) {}
        }

        private float parseFloatSafe(String s, float def) {
            try { return Float.parseFloat(s); } catch (Exception e) { return def; }
        }

        private float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }
    }

    // ======= 공통 URL 핸들러 =======
    private boolean handleUrlOverride(String url) {
        if (url == null) return false;

        // mailto:
        if (url.startsWith("mailto:")) {
            Log.d(TAG, "MAILTO -> " + url);
            try {
                MailTo mt = MailTo.parse(url);
                String to = Uri.decode(mt.getTo());
                String cc = Uri.decode(mt.getCc());
                String subject = Uri.decode(mt.getSubject());
                String body = Uri.decode(mt.getBody());

                Intent email = new Intent(Intent.ACTION_SENDTO);
                if (to != null && !to.isEmpty()) {
                    email.setData(Uri.parse("mailto:" + to));
                    email.putExtra(Intent.EXTRA_EMAIL, new String[]{ to });
                } else {
                    email.setData(Uri.parse("mailto:"));
                }
                if (subject != null && !subject.isEmpty()) email.putExtra(Intent.EXTRA_SUBJECT, subject);
                if (body != null && !body.isEmpty()) email.putExtra(Intent.EXTRA_TEXT, body);
                if (cc != null && !cc.isEmpty()) email.putExtra(Intent.EXTRA_CC, new String[]{ cc });

                startActivity(Intent.createChooser(email, "메일 앱 선택"));
            } catch (ActivityNotFoundException e) {
                try {
                    MailTo mt2 = MailTo.parse(url);
                    Intent fallback = new Intent(Intent.ACTION_SEND);
                    fallback.setType("message/rfc822");

                    String to2 = Uri.decode(mt2.getTo());
                    String cc2 = Uri.decode(mt2.getCc());
                    String subject2 = Uri.decode(mt2.getSubject());
                    String body2 = Uri.decode(mt2.getBody());

                    if (to2 != null && !to2.isEmpty()) fallback.putExtra(Intent.EXTRA_EMAIL, new String[]{ to2 });
                    if (cc2 != null && !cc2.isEmpty()) fallback.putExtra(Intent.EXTRA_CC, new String[]{ cc2 });
                    if (subject2 != null && !subject2.isEmpty()) fallback.putExtra(Intent.EXTRA_SUBJECT, subject2);
                    if (body2 != null && !body2.isEmpty()) fallback.putExtra(Intent.EXTRA_TEXT, body2);

                    startActivity(Intent.createChooser(fallback, "메일 앱 선택"));
                } catch (Exception e2) {
                    Toast.makeText(this, "메일 앱이 설치되어 있지 않습니다.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "No email app", e2);
                }
            } catch (Exception ex) {
                Log.e(TAG, "handle mailto failed: " + url, ex);
            }
            return true;
        }

        // tel:
        if (url.startsWith("tel:")) {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "전화 앱이 없습니다.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        // intent:
        if (url.startsWith("intent:")) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                try {
                    Intent fallback = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=" + extractPackageFromIntentUrl(url)));
                    startActivity(fallback);
                } catch (Exception ignored) {
                    Toast.makeText(this, "앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {}
            return true;
        }

        // market:
        if (url.startsWith("market:")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Play 스토어를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        // 구글 OAuth는 CCT
        if (isGoogleAuthUrl(url)) {
            openInCustomTab(url);
            return true;
        }

        // 그 외는 WebView 로드
        return false;
    }

    // ======= 유틸 =======
    private boolean isGoogleAuthUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null) return false;

        boolean isAccounts = host.equalsIgnoreCase("accounts.google.com");
        boolean looksLikeOAuth = url.contains("/o/oauth2") || url.contains("oauth2")
                || url.contains("ServiceLogin") || url.contains("/signin") || url.contains("challenge");
        return isAccounts && looksLikeOAuth;
    }

    private void openInCustomTab(String url) {
        try {
            CustomTabsIntent cct = new CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build();
            cct.launchUrl(this, Uri.parse(url));
        } catch (Exception e) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        }
    }

    private String extractPackageFromIntentUrl(String intentUrl) {
        try {
            int s = intentUrl.indexOf("package=");
            if (s == -1) return "";
            int e = intentUrl.indexOf(";", s);
            if (e == -1) e = intentUrl.length();
            return intentUrl.substring(s + 8, e);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyWebViewWidthLimit();   // ✅ 방향/화면 변경 시 재적용
    }

    @Override
    protected void onResume() {
        super.onResume();
        View decor = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(decor);
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(0xFFFFFFFF);
            getWindow().setNavigationBarColor(0xFF000000);
        }
        WindowInsetsControllerCompat c = ViewCompat.getWindowInsetsController(decor);
        if (c != null) {
            c.setAppearanceLightStatusBars(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.setAppearanceLightStatusBars(true);
                c.setAppearanceLightNavigationBars(false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        // ✅ TTS 정리
        ttsReady = false;
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
            try { tts.shutdown(); } catch (Exception ignored) {}
            tts = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (myWebView != null && myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            long currentTime = System.currentTimeMillis();
            if (currentTime - backPressedTime < BACK_INTERVAL) {
                finishAffinity();
                System.exit(0);
            } else {
                backPressedTime = currentTime;
                Toast.makeText(this, "뒤로 가기를 한 번 더 누르면 앱이 종료됩니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
