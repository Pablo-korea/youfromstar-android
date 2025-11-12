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
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebView.WebViewTransport;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.Keep;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;
import android.content.res.Configuration;
import androidx.annotation.NonNull;
import android.view.ViewGroup;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String HOME_URL = "https://youfromstar.odha.com/";
    private static final int BACK_INTERVAL = 2000;
    private static final int MAX_WIDTH_DP = 420;

    private WebView myWebView;
    private long backPressedTime = 0;

    private BillingHelper billing;

    // 네이티브 TTS
    private TextToSpeech tts;
    private float ttsRate = 1.0f;
    private float ttsPitch = 1.0f;
    private volatile boolean ttsReady = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        applyWebViewWidthLimit();

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

        // 👉 앱 WebView 식별용 UA 추가 (프론트에서 state=app 분기 시 사용 가능)
        String originUA = webSettings.getUserAgentString();
        webSettings.setUserAgentString(originUA + " YOUFROMSTAR_APP");

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

        // 팝업 / 새창 처리
        myWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, android.os.Message resultMsg) {
                WebView temp = new WebView(MainActivity.this);
                temp.getSettings().setJavaScriptEnabled(true);
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageStarted(WebView v, String url, Bitmap favicon) {
                        if (url != null) {
                            boolean handled = MainActivity.this.handleUrlOverride(url);
                            if (!handled && myWebView != null) {
                                myWebView.loadUrl(url);
                            }
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

        // TTS 초기화
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.KOREAN);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Korean TTS not supported or missing data");
                    try {
                        startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                    } catch (Exception ignored) {}
                }
                tts.setSpeechRate(ttsRate);
                tts.setPitch(ttsPitch);
                ttsReady = true;
                Log.d(TAG, "TTS ready");
            } else {
                Log.e(TAG, "TTS init failed: " + status);
            }
        });

        // Billing
        billing = new BillingHelper(this, myWebView);
        billing.start();
        myWebView.addJavascriptInterface(new WebAppInterface(this, billing), "AndroidBilling");

        // JS → 네이티브 TTS 브릿지
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

        // 🔹 딥링크로 실행되었는지 먼저 확인, 아니면 첫 페이지 로드
        if (!handleDeepLink(getIntent())) {
            myWebView.loadUrl(HOME_URL);
        }
    }

    // ========= 딥링크 재진입 대응 =========

    @Override
    protected void onStart() {
        super.onStart();
        handleDeepLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    // ========= JS → 네이티브 TTS 브릿지 =========

    @Keep
    public class AndroidTTSBridge {

        @JavascriptInterface @Keep
        public String ping() {
            return "pong";
        }

        @JavascriptInterface @Keep
        public void readText(final String text, final String lang,
                             final String rateStr, final String pitchStr) {
            if (text == null || text.trim().isEmpty() || tts == null) return;

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
                        int r = tts.setLanguage(Locale.forLanguageTag(lang));
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "Lang not supported: " + lang);
                        }
                    } catch (Exception ignored) {}
                }
                tts.setSpeechRate(ttsRate);
                tts.setPitch(ttsPitch);

                tts.stop();
                int res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "youfromstar-tts");
                Log.d(TAG, "tts.speak result=" + res);
            } catch (Exception e) {
                Log.e(TAG, "AndroidTTS.readText error", e);
            }
        }

        @JavascriptInterface @Keep
        public void stop() {
            try {
                if (tts != null) tts.stop();
            } catch (Exception ignored) {}
        }

        private float parseFloatSafe(String s, float def) {
            try { return Float.parseFloat(s); } catch (Exception e) { return def; }
        }

        private float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }
    }

    // ========= 딥링크 처리 =========

    private boolean handleDeepLink(Intent intent) {
        if (intent == null || myWebView == null) return false;

        Uri data = intent.getData();
        if (data == null) return false;

        String scheme = data.getScheme();
        String host = data.getHost();

        if ("youfromstar".equalsIgnoreCase(scheme)
                && "login".equalsIgnoreCase(host)) {

            String token = data.getQueryParameter("token");
            String signupNeeded = data.getQueryParameter("signup_needed");
            String userid = data.getQueryParameter("userid");

            // 기존 유저: 토큰으로 앱 내 로그인 처리
            if (token != null && !token.isEmpty()) {
                String url = HOME_URL + "auth/google/app-login?token=" + token;
                Log.d(TAG, "DeepLink login → " + url);
                myWebView.loadUrl(url);
                return true;
            }

            // 신규 유저: 앱 내 구글 회원가입 페이지로 유도
            if ("1".equals(signupNeeded) && userid != null && !userid.isEmpty()) {
                String url = HOME_URL + "signup/google?userid=" + userid;
                Log.d(TAG, "DeepLink signup → " + url);
                myWebView.loadUrl(url);
                return true;
            }
        }

        return false;
    }

    // ========= 공통 URL 핸들러 =========

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
                if (subject != null && !subject.isEmpty())
                    email.putExtra(Intent.EXTRA_SUBJECT, subject);
                if (body != null && !body.isEmpty())
                    email.putExtra(Intent.EXTRA_TEXT, body);
                if (cc != null && !cc.isEmpty())
                    email.putExtra(Intent.EXTRA_CC, new String[]{ cc });

                startActivity(Intent.createChooser(email, "메일 앱 선택"));
            } catch (Exception e) {
                Toast.makeText(this, "메일 앱이 설치되어 있지 않습니다.", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "No email app", e);
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

        // ✅ 구글 OAuth URL은 보안 브라우저(Custom Tabs)에서 처리
        if (isGoogleAuthUrl(url)) {
            openInCustomTab(url);
            return true;
        }

        // 나머지 http/https는 WebView 내에서 처리
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false;
        }

        // 그 외 스킴은 차단
        return true;
    }

    // ========= 유틸 =========

    private boolean isGoogleAuthUrl(String url) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null) return false;

            boolean isAccounts = host.equalsIgnoreCase("accounts.google.com");
            boolean looksLikeOAuth =
                    url.contains("oauth2")
                            || url.contains("ServiceLogin")
                            || url.contains("signin")
                            || url.contains("challenge");

            return isAccounts && looksLikeOAuth;
        } catch (Exception e) {
            return false;
        }
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
            lp.width = target;
            myWebView.setLayoutParams(lp);
        });
    }

    private float parseFloatSafe(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // ========= 생명주기 정리 =========

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyWebViewWidthLimit();
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
