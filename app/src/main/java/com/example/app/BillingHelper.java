// app/src/main/java/com/example/app/BillingHelper.java
package com.example.app;

import android.app.Activity;
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

    public BillingHelper(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.client = BillingClient.newBuilder(activity)
                .enablePendingPurchases()
                .setListener(this)
                .build();
    }

    public void start() {
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) { /* ready */ }
            @Override
            public void onBillingServiceDisconnected() { /* optional: retry */ }
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

                // 서버 검증 이후 acknowledge를 권장. (샘플에선 즉시)
                if (!p.isAcknowledged()) {
                    AcknowledgePurchaseParams ack = AcknowledgePurchaseParams
                            .newBuilder().setPurchaseToken(p.getPurchaseToken()).build();
                    client.acknowledgePurchase(ack, result -> {});
                }
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
