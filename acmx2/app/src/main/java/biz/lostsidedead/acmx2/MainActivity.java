package biz.lostsidedead.acmx2;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final String LOCAL_VISUALIZER_URL =
            "https://appassets.androidplatform.net/assets/visualizer/index.html";

    private WebView myWebView;
    private final MediaActionSound sound = new MediaActionSound();
    private ValueCallback<Uri[]> fileChooserCallback;
    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (fileChooserCallback == null) return;

                Uri[] results = null;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    String dataString = data.getDataString();

                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }

                fileChooserCallback.onReceiveValue(results);
                fileChooserCallback = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply fullscreen flags BEFORE setContentView to prevent layout flicker
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        setContentView(R.layout.activity_main);
        applyImmersiveMode();

        // WebView Configuration
        myWebView = findViewById(R.id.visualizer_webview);
        WebSettings settings = myWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Register the JS -> Java Bridge
        myWebView.addJavascriptInterface(new WebAppInterface(), "AndroidInterface");

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        myWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectAndroidDownloadBridge();
            }
        });

        myWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                MainActivity.this.runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }

                fileChooserCallback = filePathCallback;

                try {
                    Intent intent = fileChooserParams.createIntent();
                    fileChooserLauncher.launch(intent);
                    return true;
                } catch (Exception e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this,
                            "Unable to open file picker: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        // Request Hardware Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 101);
        }

        clearWebViewCache();
        myWebView.loadUrl(LOCAL_VISUALIZER_URL + "?v=1.7.0&t=" + System.currentTimeMillis());
    }

    /** Re-apply immersive/fullscreen mode whenever the window regains focus. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    /** Sticky immersive fullscreen – hides both status bar and navigation bar. */
    private void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            if (controller != null) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private void clearWebViewCache() {
        if (myWebView == null) return;

        myWebView.stopLoading();
        myWebView.clearCache(true);
        myWebView.clearHistory();
        myWebView.clearFormData();
        WebStorage.getInstance().deleteAllData();

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();
    }

    private void notifyProcessingOverlay(boolean visible, String statusText) {
        if (myWebView == null) return;

        String safeStatus = statusText == null
                ? ""
                : statusText
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", " ")
                .replace("\r", " ");

        String script = "(function(){" +
                "var overlay=document.getElementById('processing-overlay');" +
                "if(overlay){overlay.style.display='" + (visible ? "flex" : "none") + "';}" +
                "var status=document.getElementById('processing-status');" +
                "if(status){status.textContent='" + safeStatus + "';}" +
                "})();";

        myWebView.post(() -> myWebView.evaluateJavascript(script, null));
    }

    /**
     * Inject a page-side bridge after each load.
     * This is required because Android WebView ignores browser-style downloads
     * such as: <a download href="data:image/..."> and <a download href="blob:...">.
     * The injected code reroutes those saves through the Java bridge so they can
     * be written into MediaStore.
     */
    private void injectAndroidDownloadBridge() {
        if (myWebView == null) return;

        String script =
                "(function(){" +
                        "if(window.__acmxAndroidBridgeInstalled){return;}" +
                        "window.__acmxAndroidBridgeInstalled=true;" +
                        "const hasImage=function(){return typeof window.AndroidInterface!=='undefined'&&typeof window.AndroidInterface.saveImage==='function';};" +
                        "const hasVideo=function(){return typeof window.AndroidInterface!=='undefined'&&typeof window.AndroidInterface.startVideoSave==='function'&&typeof window.AndroidInterface.appendVideoChunk==='function'&&typeof window.AndroidInterface.finalizeVideoSave==='function';};" +
                        "const mimeFromName=function(name,fallback){const lower=String(name||'').toLowerCase();if(lower.endsWith('.mp4'))return 'video/mp4';if(lower.endsWith('.webm'))return 'video/webm';return fallback||'video/mp4';};" +
                        "const dataUrlToPayload=function(value){const text=String(value||'');const comma=text.indexOf(',');return comma>=0?text.slice(comma+1):text;};" +
                        "window.__acmxSaveBlobToAndroid=async function(blob,filename,mimeType){if(!hasVideo())return false;const safeName=filename||('acmx2_'+Date.now()+((mimeType||blob.type||'').indexOf('webm')!==-1?'.webm':'.mp4'));const finalMime=mimeType||blob.type||mimeFromName(safeName,'video/mp4');const chunkSize=1024*1024;window.AndroidInterface.startVideoSave(safeName);for(let start=0;start<blob.size;start+=chunkSize){const chunk=blob.slice(start,start+chunkSize);const base64=await new Promise(function(resolve,reject){const reader=new FileReader();reader.onloadend=function(){resolve(reader.result||'');};reader.onerror=function(){reject(reader.error||new Error('FileReader failed'));};reader.readAsDataURL(chunk);});window.AndroidInterface.appendVideoChunk(dataUrlToPayload(base64));}window.AndroidInterface.finalizeVideoSave(finalMime);return true;};" +
                        "const originalClick=HTMLAnchorElement.prototype.click;" +
                        "HTMLAnchorElement.prototype.click=function(){const href=this.getAttribute('href')||this.href||'';const filename=this.getAttribute('download')||this.download||'';const lower=String(href).toLowerCase();if(filename&&lower.indexOf('data:image/')===0&&hasImage()){window.AndroidInterface.saveImage(href);return;}if(filename&&(lower.indexOf('blob:')===0||lower.indexOf('data:video/')===0)&&hasVideo()){Promise.resolve().then(async function(){try{const response=await fetch(href);const blob=await response.blob();await window.__acmxSaveBlobToAndroid(blob,filename,blob.type||mimeFromName(filename,'video/mp4'));}catch(error){console.error('Android video save bridge failed',error);}});return;}return originalClick.apply(this,arguments);};" +
                        "})();";

        myWebView.evaluateJavascript(script, null);
    }

    /**
     * JavaScript Bridge Class
     * Handles data transfer from WebView to Android Host.
     */
    public class WebAppInterface {
        private File tempVideoFile;

        // --- SNAPSHOT SAVING ---
        @JavascriptInterface
        public void saveImage(String base64Data) {
            try {
                sound.play(MediaActionSound.SHUTTER_CLICK);
                // Use indexOf to safely strip the data-URL header
                int commaIdx = base64Data.indexOf(',');
                String cleanData = commaIdx >= 0 ? base64Data.substring(commaIdx + 1) : base64Data;
                byte[] decodedBytes = Base64.decode(cleanData, Base64.DEFAULT);
                saveBufferToGallery(decodedBytes, "image/png",
                        Environment.DIRECTORY_PICTURES, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                notifyProcessingOverlay(false, "Snapshot saved!");
            } catch (Exception e) {
                showToast("Snapshot failed: " + e.getMessage());
                notifyProcessingOverlay(false, "Snapshot failed");
            }
        }

        // --- VIDEO SAVING (Chunked to prevent RAM crashes) ---
        @JavascriptInterface
        public void startVideoSave(String filename) {
            try {
                tempVideoFile = new File(getCacheDir(), filename);
                if (tempVideoFile.exists()) tempVideoFile.delete();
                notifyProcessingOverlay(true, "Preparing video save...");
            } catch (Exception e) {
                showToast("Video save init failed: " + e.getMessage());
                notifyProcessingOverlay(false, "Video save failed");
            }
        }

        @JavascriptInterface
        public void appendVideoChunk(String base64Chunk) {
            try {
                byte[] data = Base64.decode(base64Chunk, Base64.DEFAULT);
                try (FileOutputStream fos = new FileOutputStream(tempVideoFile, true)) {
                    fos.write(data);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void finalizeVideoSave(String mimeType) {
            if (tempVideoFile != null && tempVideoFile.exists()) {
                sound.play(MediaActionSound.STOP_VIDEO_RECORDING);
                saveFileToGallery(tempVideoFile, mimeType);
                notifyProcessingOverlay(false, "Saved to Gallery!");
            } else {
                showToast("Video save failed: no data was received.");
                notifyProcessingOverlay(false, "Video save failed");
            }
        }

        private void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    private void saveFileToGallery(File file, String mimeType) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "ACMX2_VID_" + System.currentTimeMillis());
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ACMX2");

            Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri);
                     FileInputStream in = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
                runOnUiThread(() -> Toast.makeText(this, "Video saved to Gallery!", Toast.LENGTH_SHORT).show());
                file.delete();
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Video save failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void saveBufferToGallery(byte[] data, String mimeType, String folder, Uri collection) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "ACMX2_IMG_" + System.currentTimeMillis());
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder + "/ACMX2");

            Uri uri = getContentResolver().insert(collection, values);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(data);
                }
                runOnUiThread(() -> Toast.makeText(this, "Snapshot saved!", Toast.LENGTH_SHORT).show());
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Snapshot failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    @Override
    public void onBackPressed() {
        if (myWebView != null && myWebView.canGoBack()) {
            myWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}