package com.codelsur.percheo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Configuración WebView (cookies, sesión, JS).
 */
public class WebViewManager {
    private static final String TAG = "WebViewManager";

    public static final String LOGIN_URL = "https://www.isyplus.com/nfv3/login";
    public static final String CONSULTA_URL = "https://www.isyplus.com/nfv3/legacy/consultaart";

    @SuppressLint({"SetJavaScriptEnabled"})
    public static void setup(Context ctx, WebView webView) {
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(webView, true);
        }

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " PercheoApp/1.0");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "onPageFinished: " + url);
                CookieManager.getInstance().flush();
            }
        });
    }

    public static void loadLogin(WebView wv) {
        wv.loadUrl(LOGIN_URL);
    }

    /**
     * Carga consulta solo si NO estás ya en:
     * - /nfv3/legacy/consultaart
     * - o la página que contiene el iframe legacy
     * - o el iframe /v2/home?embed=1#/consultaart (a veces queda como url visible en history)
     */
    public static boolean ensureConsultaLoaded(WebView wv) {
        String u = wv.getUrl();
        if (u == null) {
            wv.loadUrl(CONSULTA_URL);
            return true; // se cargó ahora
        }

        String lu = u.toLowerCase();
        boolean already =
                lu.contains("/nfv3/legacy/consultaart") ||
                        lu.contains("/legacy/consultaart") ||
                        (lu.contains("/v2/home") && lu.contains("embed=1") && lu.contains("#/consultaart")) ||
                        lu.contains("#/consultaart");

        if (!already) {
            wv.loadUrl(CONSULTA_URL);
            return true; // se cargó ahora
        }
        return false; // ya estaba, no recargar
    }
}
