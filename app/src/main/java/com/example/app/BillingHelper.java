// app/src/main/java/com/example/app/BillingHelper.java
package com.example.app;

import android.app.Activity;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.android.billingclient.api.*;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

public class BillingHelper implements PurchasesUpdatedListener {

    private final Activity activity;
    private final WebView webView;
    private final BillingClient client;
    private static final String TAG = "BillingHelper";

    public BillingHelper(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.client = BillingClient.newBuilder(activity)
                .enablePendingPurchases()
                .setListener(this)
                .build();
    }

    public BillingClient getBillingClient() {   // ✅ MainActivity에서도 접근 가능하도록
        return client;
    }

    // ✅ Billing 연결 시 미소모 구매 자동 소비
    public void start() {
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "✅ BillingClient 연결 완료");
                    consumeUnfinishedPurchases(); // ✅ 미소모 구매 자동 소비
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.w(TAG, "⚠️ BillingService 연결 끊김 - 재연결 시도 가능");
            }
        });
    }

    // ✅ 남아 있는 미소모 구매를 모두 소비
    private void consumeUnfinishedPurchases() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        client.queryPurchasesAsync(params, (billingResult, purchasesList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchasesList != null) {
                for (Purchase p : purchasesList) {
                    ConsumeParams consumeParams = ConsumeParams.newBuilder()
                            .setPurchaseToken(p.getPurchaseToken())
                            .build();
                    client.consumeAsync(consumeParams, (result, token) -> {
                        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "✅ 미소모 구매 소비 완료: " + token);
                        } else {
                            Log.w(TAG, "⚠️ 소비 실패: " + result.getResponseCode());
                        }
                    });
                }
            }
        });
    }

    public void queryAndBuy(String productId) {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                )).build();

        client.queryProductDetailsAsync(params, (result, detailsList) -> {
            if (detailsList == null || detailsList.isEmpty()) {
                sendJs("ERROR", null, null, null, "상품을 찾을 수 없습니다: " + productId);
                return;
            }
            ProductDetails details = detailsList.get(0);
            BillingFlowParams.ProductDetailsParams pdp =
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build();
            BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(pdp))
                    .build();
            client.launchBillingFlow(activity, flowParams);
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult r, List<Purchase> purchases) {
        int code = r.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) {
                String productId = p.getProducts().isEmpty() ? "" : p.getProducts().get(0);
                sendJs("OK", productId, p.getPurchaseToken(), p.getOrderId(), null);

                // ✅ 결제 후 즉시 consume (소모형 상품은 acknowledge 대신)
                ConsumeParams consumeParams = ConsumeParams.newBuilder()
                        .setPurchaseToken(p.getPurchaseToken())
                        .build();
                client.consumeAsync(consumeParams, (result, token) -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        Log.d(TAG, "✅ 결제 후 즉시 소비 완료: " + token);
                    } else {
                        Log.w(TAG, "⚠️ 결제 소비 실패: " + result.getResponseCode());
                    }
                });
            }
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            sendJs("CANCELED", null, null, null, null);
        } else {
            sendJs("ERROR", null, null, null, r.getDebugMessage());
        }
    }

    private void sendJs(String status, String productId, String token, String orderId, String message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("status", status);
            if (productId != null) obj.put("productId", productId);
            if (token != null) obj.put("purchaseToken", token);
            if (orderId != null) obj.put("orderId", orderId);
            if (message != null) obj.put("message", message);

            String script = "window.onBillingResult && window.onBillingResult(" + obj.toString() + ");";
            activity.runOnUiThread(() -> webView.evaluateJavascript(script, null));
        } catch (Exception ignored) {}
    }
}
