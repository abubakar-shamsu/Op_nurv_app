package com.optimspark.nurvle;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {
    private WebView mWebView;
    private NetworkCallback networkCallback;
    private ValueCallback<Uri[]> filePathCallback;
    
    // Permission request codes
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 101;
    private static final int MICROPHONE_PERMISSION_REQUEST_CODE = 102;
    
    // SharedPreferences for tracking permission state
    private static final String PREFS_NAME = "NurvlePrefs";
    private static final String PERMISSIONS_GRANTED_KEY = "permissions_granted";
    private SharedPreferences sharedPreferences;
    
    private static final String TAG = "NurvleApp";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.activity_main_webview);
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Enhanced JavaScript interface for file operations
        mWebView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void downloadBase64File(String textContent, String fileName) {
                try {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File filePath = new File(downloadsDir, fileName);
                    
                    FileOutputStream os = new FileOutputStream(filePath);
                    os.write(textContent.getBytes());
                    os.flush();
                    os.close();

                    runOnUiThread(() -> 
                        Toast.makeText(getApplicationContext(), "response saved: " + fileName, Toast.LENGTH_LONG).show()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> 
                        Toast.makeText(getApplicationContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }

            @JavascriptInterface
            public void downloadTextFile(String content, String fileName) {
                try {
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(content.getBytes());
                    fos.close();
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Saved: " + fileName, Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Save failed", Toast.LENGTH_SHORT).show());
                }
            }

            @JavascriptInterface
            public void downloadImageFromUrl(String imageUrl, String fileName) {
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(imageUrl));
                    request.setMimeType("image/png");
                    request.setDescription("Downloading generated image...");
                    request.setTitle(fileName);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                    // Always save to public Downloads (works on all versions, including Android 10+)
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    dm.enqueue(request);
                    
                    runOnUiThread(() -> 
                        Toast.makeText(getApplicationContext(), "Downloading image: " + fileName, Toast.LENGTH_LONG).show()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> 
                        Toast.makeText(getApplicationContext(), "Failed to download image: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }

            @android.webkit.JavascriptInterface
            public void openInBrowser(String url) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> 
                        Toast.makeText(getApplicationContext(), "Cannot open URL: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }

            @android.webkit.JavascriptInterface
            public boolean hasCameraPermission() {
                return ContextCompat.checkSelfPermission(MainActivity.this, 
                    android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            }

            @android.webkit.JavascriptInterface
            public boolean hasMicrophonePermission() {
                return ContextCompat.checkSelfPermission(MainActivity.this, 
                    android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            }

            @android.webkit.JavascriptInterface
            public void requestCameraPermission() {
                runOnUiThread(() -> {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, 
                        android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{android.Manifest.permission.CAMERA},
                            CAMERA_PERMISSION_REQUEST_CODE);
                    }
                });
            }

            @android.webkit.JavascriptInterface
            public void requestMicrophonePermission() {
                runOnUiThread(() -> {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, 
                        android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{android.Manifest.permission.RECORD_AUDIO},
                            MICROPHONE_PERMISSION_REQUEST_CODE);
                    }
                });
            }

            @JavascriptInterface
            public void showToast(String message) {
                runOnUiThread(() -> 
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show()
                );
            }

        }, "Android");

        // Request permissions only if not already granted
        if (!areAllPermissionsGranted() && !sharedPreferences.getBoolean(PERMISSIONS_GRANTED_KEY, false)) {
            requestNecessaryPermissions();
        }

        // WebView configuration
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        
        // Enable media playback
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        // Cookie management
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(mWebView, true);

        // Enhanced WebView client with proper link handling
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.d(TAG, "Loading URL: " + url);
                
                // Handle external URLs in browser
                if (url.startsWith("http") && !url.contains("n0loq7r9a2zb3xn4k8yp6tm5wv1ucqjhf.netlify.app")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        Log.e(TAG, "Error opening URL in browser: " + e.getMessage());
                        // If browser fails, load in WebView
                        view.loadUrl(url);
                        return true;
                    }
                }
                
                // Handle tel:, mailto:, sms: etc.
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") || url.startsWith("whatsapp:")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        Log.e(TAG, "Error opening system intent: " + e.getMessage());
                        return true;
                    }
                }
                
                // Load internal URLs in WebView
                return false;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished loading: " + url);
                injectJavaScript();
            }
        });
        
        // Enhanced WebChromeClient for file uploads, camera, microphone, and window handling
        mWebView.setWebChromeClient(new WebChromeClient() {
            // For file upload support
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, 1);
                } catch (Exception e) {
                    filePathCallback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
            
            // Handle permission requests for camera and microphone
            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                String[] resources = request.getResources();
                for (String resource : resources) {
                    switch (resource) {
                        case android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE:
                            if (ContextCompat.checkSelfPermission(MainActivity.this, 
                                android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                request.grant(new String[]{resource});
                            } else {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{android.Manifest.permission.CAMERA},
                                    CAMERA_PERMISSION_REQUEST_CODE);
                            }
                            break;
                        case android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE:
                            if (ContextCompat.checkSelfPermission(MainActivity.this, 
                                android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                request.grant(new String[]{resource});
                            } else {
                                ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{android.Manifest.permission.RECORD_AUDIO},
                                    MICROPHONE_PERMISSION_REQUEST_CODE);
                            }
                            break;
                    }
                }
            }
            
            // Handle new windows - for openFullBtn functionality
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                // Get the URL that's trying to open in new window
                WebView.HitTestResult result = view.getHitTestResult();
                String url = result.getExtra();
                
                if (url != null && !url.isEmpty()) {
                    Log.d(TAG, "Opening URL in same WebView: " + url);
                    // Load the URL in the same WebView instead of new window
                    view.loadUrl(url);
                }
                
                // We don't create a new window, so return false
                return false;
            }
        });

        // Enhanced download listener
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimetype);
                request.addRequestHeader("cookie", CookieManager.getInstance().getCookie(url));
                request.addRequestHeader("User-Agent", userAgent);
                request.setDescription("Downloading file...");
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                request.setTitle(fileName);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                // Set destination based on Android version
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    request.setDestinationInExternalFilesDir(getApplicationContext(), Environment.DIRECTORY_DOWNLOADS, fileName);
                } else {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                }

                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.enqueue(request);
                    Toast.makeText(getApplicationContext(), "Downloading " + fileName, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(), "Download service unavailable", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getApplicationContext(), "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        // Load URL based on network availability
        if (isNetworkAvailable()) {
            mWebView.loadUrl("n0loq7r9a2zb3xn4k8yp6tm5wv1ucqjhf.netlify.app");
        } else {
            mWebView.loadUrl("file:///android_asset/offline.html");
        }

        // Network monitoring
        networkCallback = new NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    String currentUrl = mWebView.getUrl();
                    if (currentUrl != null && currentUrl.startsWith("file:///android_asset")) {
                        mWebView.loadUrl("n0loq7r9a2zb3xn4k8yp6tm5wv1ucqjhf.netlify.app");
                    }
                });
            }
            
            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> {
                    if (mWebView.getUrl() != null && !mWebView.getUrl().startsWith("file:///android_asset")) {
                        mWebView.loadUrl("file:///android_asset/offline.html");
                    }
                });
            }
        };
        
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        }
    }

    private void injectJavaScript() {
        String javascriptCode = 
            "(function() {" +
            "    // Override download functions to use Android interface" +
            "    if (typeof window.Android !== 'undefined') {" +
            "        console.log('Android interface detected, setting up download handlers...');" +
            "        " +
            "        // Override conversation download" +
            "        const originalDownloadConversation = window.downloadConversation;" +
            "        if (originalDownloadConversation) {" +
            "            window.downloadConversation = function(conversationId) {" +
            "                const conversation = window.conversations.find(c => c.id === conversationId);" +
            "                if (!conversation) return;" +
            "                " +
            "                let conversationText = 'Nurvle Conversation\\n';" +
            "                conversationText += 'Title: ' + (conversation.title || 'Untitled') + '\\n';" +
            "                conversationText += 'Date: ' + new Date(conversation.timestamp).toLocaleDateString() + '\\n';" +
            "                conversationText += 'Time: ' + new Date(conversation.timestamp).toLocaleTimeString() + '\\n';" +
            "                conversationText += '='.repeat(50) + '\\n\\n';" +
            "                " +
            "                conversation.messages.forEach((message, index) => {" +
            "                    const isUser = message.role === 'user';" +
            "                    const sender = isUser ? 'USER' : 'ASSISTANT';" +
            "                    const content = message.content || '';" +
            "                    " +
            "                    if (index > 0) {" +
            "                        conversationText += '\\n' + '-'.repeat(50) + '\\n\\n';" +
            "                    }" +
            "                    " +
            "                    conversationText += sender + ':\\n';" +
            "                    conversationText += content + '\\n';" +
            "                    " +
            "                    if (isUser && message.files && message.files.length > 0) {" +
            "                        conversationText += '\\nAttachments:\\n';" +
            "                        message.files.forEach(file => {" +
            "                            conversationText += '- ' + file.name + ' (' + file.type + ', ' + (file.size / 1024).toFixed(2) + ' KB)\\n';" +
            "                        });" +
            "                    }" +
            "                });" +
            "                " +
            "                conversationText += '\\n' + '='.repeat(50) + '\\n';" +
            "                conversationText += 'End of conversation\\n';" +
            "                " +
            "                const fileName = 'nurvle_conversation_' + conversationId + '_' + Date.now() + '.txt';" +
            "                window.Android.downloadTextFile(conversationText, fileName);" +
            "            };" +
            "        }" +
            "        " +
            "        // Override image download" +
            "        const originalDownloadGeneratedImage = window.downloadGeneratedImage;" +
            "        if (originalDownloadGeneratedImage) {" +
            "            window.downloadGeneratedImage = function(imageUrl, prompt) {" +
            "                const safePrompt = prompt.toLowerCase().replace(/[^a-z0-9]/g, '_').substring(0, 30);" +
            "                const fileName = 'generated_' + safePrompt + '_' + Date.now() + '.png';" +
            "                window.Android.downloadImageFromUrl(imageUrl, fileName);" +
            "            };" +
            "        }" +
            "        " +
            "        // Override response download" +
            "        const originalDownloadResponse = window.downloadResponse;" +
            "        if (originalDownloadResponse) {" +
            "            window.downloadResponse = function(textContent, messageId) {" +
            "                const fileName = 'response_' + messageId + '_' + Date.now() + '.txt';" +
            "                window.Android.downloadTextFile(textContent, fileName);" +
            "            };" +
            "        }" +
            "        " +
            "        // Fix Open Full button to open in same WebView instead of new window" +
            "        const overrideWindowOpen = function() {" +
            "            const originalWindowOpen = window.open;" +
            "            window.open = function(url, name, specs, replace) {" +
            "                console.log('window.open called with URL:', url);" +
            "                " +
            "                // If it's an image URL from openFullBtn, load it in the same WebView" +
            "                if (url && (url.includes('.png') || url.includes('.jpg') || url.includes('.jpeg') || url.includes('.gif') || url.includes('.webp'))) {" +
            "                    console.log('Image URL detected, loading in same WebView:', url);" +
            "                    window.location.href = url;" +
            "                    return window;" +
            "                }" +
            "                " +
            "                // For other URLs, use the original behavior" +
            "                return originalWindowOpen.call(window, url, name, specs, replace);" +
            "            };" +
            "        };" +
            "        " +
            "        // Run the overrides" +
            "        overrideWindowOpen();" +
            "        " +
            "        // Enhanced camera button functionality" +
            "        const cameraBtn = document.getElementById('camera-btn');" +
            "        if (cameraBtn) {" +
            "            cameraBtn.addEventListener('click', function() {" +
            "                if (!window.Android.hasCameraPermission()) {" +
            "                    window.Android.requestCameraPermission();" +
            "                    window.Android.showToast('Camera permission required');" +
            "                }" +
            "            });" +
            "        }" +
            "        " +
            "        // Enhanced microphone button functionality" +
            "        const micBtn = document.getElementById('mic-btn');" +
            "        if (micBtn) {" +
            "            micBtn.addEventListener('click', function() {" +
            "                if (!window.Android.hasMicrophonePermission()) {" +
            "                    window.Android.requestMicrophonePermission();" +
            "                    window.Android.showToast('Microphone permission required');" +
            "                }" +
            "            });" +
            "        }" +
            "    } else {" +
            "        console.log('Android interface not available');" +
            "    }" +
            "})();";
        
        mWebView.evaluateJavascript(javascriptCode, null);
    }

    private boolean areAllPermissionsGranted() {
        String[] permissions = new String[]{
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        };
        
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestNecessaryPermissions() {
        // Request all necessary permissions at once
        String[] permissions = new String[]{
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        };
        
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                // Only show toast if this is the first time permissions are granted
                if (!sharedPreferences.getBoolean(PERMISSIONS_GRANTED_KEY, false)) {
                    Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
                    // Save that permissions have been granted
                    sharedPreferences.edit().putBoolean(PERMISSIONS_GRANTED_KEY, true).apply();
                }
            } else {
                Toast.makeText(this, "Some permissions were denied - some features may not work", Toast.LENGTH_LONG).show();
            }
        }
        
        // Notify WebView about permission changes
        mWebView.reload();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return false;
        
        Network nw = connectivityManager.getActiveNetwork();
        if (nw == null) return false;
        
        NetworkCapabilities actNw = connectivityManager.getNetworkCapabilities(nw);
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
               actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || 
               actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || 
               actNw.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            }
        }
    }
}
