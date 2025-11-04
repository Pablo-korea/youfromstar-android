// app/src/main/java/com/example/app/WebAppInterface.java
package com.example.app;

import android.app.Activity;
import android.webkit.JavascriptInterface;

public class WebAppInterface {
    private final Activity activity;
    private final BillingHelper billing;

    public WebAppInterface(Activity activity, BillingHelper billing) {
        this.activity = activity;
        this.billing = billing;
    }

    @JavascriptInterface
    public void buy(String productId) {
        activity.runOnUiThread(() -> billing.queryAndBuy(productId));
    }
}
