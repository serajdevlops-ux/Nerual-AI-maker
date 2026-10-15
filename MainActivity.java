package com.seraj.neuralmapmaker;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Base64;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Could not open file picker", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
        webView.addJavascriptInterface(new AppBridge(), "AndroidBridge");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class AppBridge {
        @JavascriptInterface
        public void saveFile(String fileName, String mimeType, String base64Data) {
            try {
                byte[] data;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    data = Base64.getDecoder().decode(base64Data);
                } else {
                    data = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                }
                String safeName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                    values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("Could not create download");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new Exception("Could not open download");
                        out.write(data);
                    }
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) throw new Exception("Downloads folder unavailable");
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, safeName);
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(data);
                    }
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "Saved " + safeName + " to Downloads", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
