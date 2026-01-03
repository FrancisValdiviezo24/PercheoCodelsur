package com.codelsur.percheo;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

/**
 * Puente JS -> Android.
 * La web (WebView) llama: window.AndroidBridge.onProductJson(JSON.stringify(...))
 */
public class WebAppBridge {

    public interface Listener {
        void onProductReceived(Product p);
        void onScrapeError(String message);
    }

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    public WebAppBridge(Listener listener) {
        this.listener = listener;
    }

    @JavascriptInterface
    public void onProductJson(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String code = o.optString("code", "");
            String desc = o.optString("description", "");
            String pvp = o.optString("pvp", "");
            Product p = new Product(code, desc, pvp);
            main.post(() -> listener.onProductReceived(p));
        } catch (Exception e) {
            main.post(() -> listener.onScrapeError("JSON inválido: " + e.getMessage()));
        }
    }

    @JavascriptInterface
    public void onError(String message) {
        main.post(() -> listener.onScrapeError(message));
    }
}
