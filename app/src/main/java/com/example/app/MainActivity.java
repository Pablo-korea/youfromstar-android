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
import androidx.appcompat.app.AlertDialog;   // ✅ 종료 확인 다이얼로그용 import
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsSession;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;
import android.content.res.Configuration;
import androidx.annotation.NonNull;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String HOME_URL = "https://youfromstar.odha.com/";
    private static final int MAX_WIDTH_DP = 440;

    private static final int BACK_INTERVAL = 500;  // 1초 이내 두 번 눌렀을 때만 종료팝업
    private static final String MAIN_URL = HOME_URL + "static/main/";   // 메인 메뉴 URL    private static final int MAX_WIDTH_DP = 440;

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
    // ✅ 여기에서 신규/기존 유저 분기 후 WebView URL 결정

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

            Log.d(TAG, "딥링크 수신 - token: " + (token != null ? "있음" : "없음") +
                    ", signup_needed: " + signupNeeded + ", userid: " + userid);

            // ✅ 기존 유저: 토큰으로 앱 내 로그인 처리
            if (token != null && !token.isEmpty()) {
                String url = HOME_URL + "auth/google/app-login?token=" + Uri.encode(token);
                Log.d(TAG, "DeepLink login → " + url);
                myWebView.loadUrl(url);
                return true;
            }

            // ✅ 신규 유저: 회원가입 화면으로 이동 (/signup)
            if ("1".equals(signupNeeded)) {
                String url;
                if (userid != null && !userid.isEmpty()) {
                    url = HOME_URL + "signup?provider=google&userid=" + Uri.encode(userid);
                } else {
                    // userid 없더라도 최소한 구글 회원가입 플로우 진입
                    url = HOME_URL + "signup?provider=google";
                }
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

        // ✅ youfromstar:// 딥링크 처리 (Custom Tabs에서 리다이렉트된 경우)
        if (url.startsWith("youfromstar://")) {
            Log.d(TAG, "딥링크 감지 (URL 핸들러): " + url);
            try {
                Uri uri = Uri.parse(url);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.setPackage(getPackageName());
                startActivity(intent);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "딥링크 처리 실패", e);
                // 예외 상황에서도 토큰/회원가입 정보 직접 파싱
                if (url.contains("token=")) {
                    String token = extractTokenFromUrl(url);
                    if (token != null && myWebView != null) {
                        String targetUrl = HOME_URL + "auth/google/app-login?token=" + Uri.encode(token);
                        Log.d(TAG, "Fallback DeepLink login → " + targetUrl);
                        myWebView.loadUrl(targetUrl);
                        return true;
                    }
                } else if (url.contains("signup_needed=1")) {
                    // userid 있으면 같이 넘기고, 없어도 최소 /signup 진입
                    String userid = extractUseridFromUrl(url);
                    String targetUrl;
                    if (userid != null && !userid.isEmpty()) {
                        targetUrl = HOME_URL + "signup?provider=google&userid=" + Uri.encode(userid);
                    } else {
                        targetUrl = HOME_URL + "signup?provider=google";
                    }
                    if (myWebView != null) {
                        Log.d(TAG, "Fallback DeepLink signup → " + targetUrl);
                        myWebView.loadUrl(targetUrl);
                        return true;
                    }
                }
            }
            return true;
        }

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
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setShowTitle(true);

            CustomTabsIntent cct = builder.build();

            Intent intent = cct.intent;
            intent.setData(Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            cct.launchUrl(this, Uri.parse(url));

            Log.d(TAG, "Custom Tabs 열림: " + url);
        } catch (Exception e) {
            Log.e(TAG, "Custom Tabs 열기 실패", e);
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        }
    }

    private String extractTokenFromUrl(String url) {
        try {
            int start = url.indexOf("token=");
            if (start == -1) return null;
            start += 6; // "token=" 길이
            int end = url.indexOf("&", start);
            if (end == -1) end = url.length();
            return url.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUseridFromUrl(String url) {
        try {
            int start = url.indexOf("userid=");
            if (start == -1) return null;
            start += 7; // "userid=" 길이
            int end = url.indexOf("&", start);
            if (end == -1) end = url.length();
            return url.substring(start, end);
        } catch (Exception e) {
            return null;
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

    // ========= 🔴 뒤로가기 처리 + 종료 확인 다이얼로그 =========

    @Override
    public void onBackPressed() {
        if (myWebView == null) {
            super.onBackPressed();
            return;
        }

        long now = System.currentTimeMillis();
        boolean isDoublePress = (now - backPressedTime) <= BACK_INTERVAL;
        backPressedTime = now;

        // 1️⃣ 1초 이내 두 번 연속 백 → 어디서든 종료 안내 팝업
        if (isDoublePress) {
            showExitDialog();
            return;
        }

        // 2️⃣ 현재 URL 기준으로 메뉴 분기
        String currentUrl = myWebView.getUrl();
        if (currentUrl != null) {
            try {
                Uri uri = Uri.parse(currentUrl);
                String path = uri.getPath();   // 예: /static/chat/counselor/, /static/member/, /static/today/, /static/main/

                // (1) 메인(/static/main/)에서 단발 뒤로 → 종료 안내 팝업
                if (isMainPath(path)) {
                    showExitDialog();
                    return;
                }

                // (2) today / member / chat/counselor 에서 단발 뒤로 → 메인으로 이동
                if (isTodayMemberChatPath(path)) {
                    myWebView.loadUrl(MAIN_URL);   // https://youfromstar.odha.com/static/main/
                    return;
                }

            } catch (Exception ignored) {
            }
        }

        // 3️⃣ 그 외 화면은 기존 WebView 히스토리
        if (myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            showExitDialog();
        }
    }

    // ========= 생명주기 정리 =========

    private String normalizePath(String path) {
        if (path == null) return "";
        // 쿼리 제거
        int qIdx = path.indexOf("?");
        if (qIdx != -1) {
            path = path.substring(0, qIdx);
        }
        path = path.trim();

        // 끝에 슬래시 하나만 날리기 (루트 "/"는 유지)
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    // 메인 화면 경로 판별
// 메인 화면 여부: /, /static/main, /static/main/
    private boolean isMainPath(String rawPath) {
        String path = normalizePath(rawPath);

        return path.equals("")            // null → "" 처리된 경우
                || path.equals("/")      // 루트
                || path.equals("/static/main");  // 우리가 쓰는 메인
    }

    // today / member / chat/counselor 화면 여부
    private boolean isTodayMemberChatPath(String rawPath) {
        String path = normalizePath(rawPath).toLowerCase();

        return path.equals("/static/today")
                || path.equals("/static/member")
                || path.equals("/static/chat/counselor");
    }
    private void showExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle("앱 종료")
                .setMessage("정말 별당을 종료하시겠습니까?")
                .setPositiveButton("예", (dialog, which) -> {
                    dialog.dismiss();

                    // ✅ 1) WebView 쿠키 삭제
                    try {
                        CookieManager cookieManager = CookieManager.getInstance();
                        cookieManager.removeAllCookies(null);   // async
                        cookieManager.flush();
                    } catch (Exception ignored) {}

                    // ✅ 2) WebView LocalStorage 삭제
                    try {
                        if (myWebView != null) {
                            myWebView.clearCache(true);
                            myWebView.clearHistory();
                            myWebView.clearFormData();
                        }
                        WebStorage.getInstance().deleteAllData(); // localStorage 삭제
                    } catch (Exception ignored) {}

                    // 필요하면 SharedPreferences 삭제도 추가 가능
                    // getSharedPreferences("app", MODE_PRIVATE).edit().clear().apply();

                    // ✅ 3) 앱 종료
                    finish();
                })
                .setNegativeButton("아니오", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }
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
}