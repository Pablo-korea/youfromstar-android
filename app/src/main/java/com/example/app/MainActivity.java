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
import android.speech.tts.TextToSpeech;          // ✅ 추가: TTS
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;       // ✅ 추가: JS 브릿지 어노테이션
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebView.WebViewTransport;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

// ✅ insets 유틸 (시스템바 높이만큼 패딩)
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Locale;                          // ✅ 추가: 언어 태그용

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private WebView myWebView;
    private static final String HOME_URL = "https://youfromstar.odha.com/";
    private long backPressedTime = 0;
    private static final int BACK_INTERVAL = 2000;

    private BillingHelper billing;

    // ✅ 추가: 네이티브 TTS 인스턴스
    private TextToSpeech tts;
    private float ttsRate = 1.0f;
    private float ttsPitch = 1.0f;

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

        // One UI 기기에서 bars를 “보이도록” 명시
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(decor);
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        }

        setContentView(R.layout.activity_main);
        myWebView = findViewById(R.id.webview);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

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
        webSettings.setMediaPlaybackRequiresUserGesture(false); // ✅ 음성 관련 제스처 완화
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        myWebView.clearCache(true);
        myWebView.clearHistory();

        // 시스템바 인셋만큼 안전 패딩(특히 하단) 부여 → 겹침 방지
        ViewCompat.setOnApplyWindowInsetsListener(myWebView, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets; // 소비하지 않음
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
                            if (!handled) {
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

        // ✅ 네이티브 TTS 초기화
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.KOREAN);
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Korean TTS not supported, keep default");
                }
                tts.setSpeechRate(ttsRate);
                tts.setPitch(ttsPitch);
            } else {
                Log.e(TAG, "TTS init failed: " + status);
            }
        });

        // Billing
        billing = new BillingHelper(this, myWebView);
        billing.start();
        myWebView.addJavascriptInterface(new WebAppInterface(this, billing), "AndroidBilling");

        // ✅ 추가: 프론트에서 window.AndroidTTS.* 사용 가능
        myWebView.addJavascriptInterface(new AndroidTTSBridge(), "AndroidTTS");

        // 상태바/내비바 색
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(0xFFFFFFFF); // 흰색
            getWindow().setNavigationBarColor(0xFF000000);  // 검정
        }
        decor = getWindow().getDecorView();
        WindowInsetsControllerCompat c =
                ViewCompat.getWindowInsetsController(decor);
        if (c != null) {
            c.setAppearanceLightStatusBars(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.setAppearanceLightStatusBars(true);  // 흰 배경 + 검정 아이콘
                c.setAppearanceLightNavigationBars(false);
            }
        }

        // 첫 페이지 로드
        myWebView.loadUrl(HOME_URL);
    }

    // ======= ✅ JS → 네이티브 TTS 브릿지 =======
    public class AndroidTTSBridge {
        @JavascriptInterface
        public void readText(String text, String lang, String rateStr, String pitchStr) {
            if (text == null || text.trim().isEmpty() || tts == null) return;

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
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "youfromstar-tts");
            } catch (Exception e) {
                Log.e(TAG, "AndroidTTS.readText error", e);
            }
        }

        @JavascriptInterface
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
            getWindow().setStatusBarColor(0xFFFFFFFF); // 흰색
            getWindow().setNavigationBarColor(0xFF000000);  // 검정
        }
        WindowInsetsControllerCompat c = ViewCompat.getWindowInsetsController(decor);
        if (c != null) {
            c.setAppearanceLightStatusBars(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                c.setAppearanceLightStatusBars(true); // 흰 배경 + '검정' 아이콘
                c.setAppearanceLightNavigationBars(false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        // ✅ TTS 정리
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
