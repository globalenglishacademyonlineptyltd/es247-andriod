package za.co.es247.client;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 247;
    private static final String CLIENT_PORTAL_URL = "https://securitysolutionses247.co.za/client-app";
    private static final String COOKIE_NAME = "es247_session";
    private static final String PREFS_NAME = "es247_secure_session";
    private static final String PREF_COOKIE = "encrypted_cookie";
    private static final String KEY_ALIAS = "es247_session_key";

    private WebView webView;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new SessionBridge(), "ES247SessionBridge");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.startsWith("https://securitysolutionses247.co.za/")) {
                    installSessionHooks(view);
                    persistCurrentCookie();
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                    GeolocationPermissions.Callback callback) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED
                        && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                            != PackageManager.PERMISSION_GRANTED) {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    requestPermissions(
                            new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            },
                            LOCATION_REQUEST);
                } else {
                    callback.invoke(origin, true, false);
                }
            }
        });

        openPortalWithRestoredSession();
    }

    private void openPortalWithRestoredSession() {
        CookieManager manager = CookieManager.getInstance();
        String existing = manager.getCookie(CLIENT_PORTAL_URL);
        if (extractSessionCookie(existing) != null) {
            persistCurrentCookie();
            webView.loadUrl(CLIENT_PORTAL_URL);
            return;
        }

        String savedCookie = readEncryptedCookie();
        if (savedCookie == null) {
            webView.loadUrl(CLIENT_PORTAL_URL);
            return;
        }

        String restoredCookie = savedCookie + "; Path=/; Max-Age=2592000; Secure; HttpOnly; SameSite=Strict";
        manager.setCookie(CLIENT_PORTAL_URL, restoredCookie, accepted -> {
            manager.flush();
            webView.post(() -> webView.loadUrl(CLIENT_PORTAL_URL));
        });
    }

    private void installSessionHooks(WebView view) {
        String script = "(function(){if(window.__es247SessionHooksInstalled)return;"
                + "window.__es247SessionHooksInstalled=true;"
                + "var originalFetch=window.fetch.bind(window);"
                + "window.fetch=function(input,init){var raw=(typeof input==='string'?input:(input&&input.url)||'');"
                + "return originalFetch(input,init).then(function(response){"
                + "if(raw.indexOf('/api/auth/logout')!==-1&&response.ok){window.ES247SessionBridge.sessionCleared();}"
                + "if(raw.indexOf('/api/auth/login')!==-1&&response.ok){window.ES247SessionBridge.sessionEstablished();}"
                + "if(raw.indexOf('/api/me')!==-1&&response.status===401){window.ES247SessionBridge.sessionCleared();}"
                + "return response;});};})();";
        view.evaluateJavascript(script, null);
    }

    private String extractSessionCookie(String cookieHeader) {
        if (cookieHeader == null) return null;
        for (String part : cookieHeader.split(";")) {
            String cookie = part.trim();
            if (cookie.startsWith(COOKIE_NAME + "=") && cookie.length() > COOKIE_NAME.length() + 1) {
                return cookie;
            }
        }
        return null;
    }

    private void persistCurrentCookie() {
        String cookie = extractSessionCookie(CookieManager.getInstance().getCookie(CLIENT_PORTAL_URL));
        if (cookie == null) return;
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey key;
            if (keyStore.containsAlias(KEY_ALIAS)) {
                key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            } else {
                KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build());
                key = generator.generateKey();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(cookie.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            String packed = Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_COOKIE, packed).apply();
        } catch (Exception ignored) {
            // The regular WebView cookie remains available even if secure backup storage fails.
        }
    }

    private String readEncryptedCookie() {
        String packed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_COOKIE, null);
        if (packed == null) return null;
        try {
            String[] pieces = packed.split(":", 2);
            if (pieces.length != 2) return null;
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            if (key == null) return null;
            byte[] iv = Base64.decode(pieces[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(pieces[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            String cookie = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            return cookie.startsWith(COOKIE_NAME + "=") ? cookie : null;
        } catch (Exception ignored) {
            clearPersistedSession();
            return null;
        }
    }

    private void clearPersistedSession() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(PREF_COOKIE).apply();
    }

    private class SessionBridge {
        @JavascriptInterface
        public void sessionEstablished() {
            runOnUiThread(() -> {
                CookieManager.getInstance().flush();
                persistCurrentCookie();
            });
        }

        @JavascriptInterface
        public void sessionCleared() {
            runOnUiThread(() -> {
                clearPersistedSession();
                CookieManager.getInstance().setCookie(CLIENT_PORTAL_URL,
                        COOKIE_NAME + "=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Strict");
                CookieManager.getInstance().flush();
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST && pendingGeoCallback != null) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
    }

    @Override
    protected void onPause() {
        persistCurrentCookie();
        CookieManager.getInstance().flush();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onStop() {
        persistCurrentCookie();
        CookieManager.getInstance().flush();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        persistCurrentCookie();
        CookieManager.getInstance().flush();
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("ES247SessionBridge");
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
