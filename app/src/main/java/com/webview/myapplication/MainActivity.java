package com.optimspark.nurvle;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.content.Intent;
import android.util.Base64;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends Activity {
    private WebView mWebView;
    private NetworkCallback networkCallback;
    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.activity_main_webview);

        // Enhanced JavaScript interface for file operations
        mWebView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void downloadBase64File(String base64Data, String fileName) {
                try {
                    // Remove data URL prefix if present
                    if (base64Data.contains(",")) {
                        base64Data = base64Data.split(",")[1];
                    }
                    
                    byte[] fileAsBytes = Base64.decode(base64Data, Base64.DEFAULT);
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File filePath = new File(downloadsDir, fileName);
                    
                    FileOutputStream os = new FileOutputStream(filePath);
                    os.write(fileAsBytes);
                    os.flush();
                    os.close();

                    runOnUiThread(() -> 
                        Toast.makeText(getApplicationContext(), "Saved: " + fileName, Toast.LENGTH_LONG).show()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> 
                        Toast.makeText(getApplicationContext(), "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }

            @android.webkit.JavascriptInterface
            public void downloadTextFile(String textContent, String fileName) {
                try {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File filePath = new File(downloadsDir, fileName);
                    
                    FileOutputStream os = new FileOutputStream(filePath);
                    os.write(textContent.getBytes());
                    os.flush();
                    os.close();

                    runOnUiThread(() -> 
                        Toast.makeText(getApplicationContext(), "Conversation saved: " + fileName, Toast.LENGTH_LONG).show()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> 
                        Toast.makeText(getApplicationContext(), "Failed to save conversation: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }

            @android.webkit.JavascriptInterface
            public void downloadImageFromUrl(String imageUrl, String fileName) {
                try {
                    // For external URLs, use download manager
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(imageUrl));
                    request.setMimeType("image/png");
                    request.setDescription("Downloading generated image...");
                    request.setTitle(fileName);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        request.setDestinationInExternalFilesDir(getApplicationContext(), Environment.DIRECTORY_DOWNLOADS, fileName);
                    } else {
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    }

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
        }, "Android");

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

        // Cookie management
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(mWebView, true);

        // Enhanced WebView client
        mWebView.setWebViewClient(new HelloWebViewClient());
        
        // Enhanced WebChromeClient for file uploads and other Chrome features
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
            mWebView.loadUrl("https://x0loq7r9a2zb3xn4k8yp6tm5wv1ucqjhf.netlify.app");
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
                        mWebView.loadUrl("https://x0loq7r9a2zb3xn4k8yp6tm5wv1ucqjhf.netlify.app");
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

    private static class HelloWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            // Handle external URLs in browser, internal URLs in WebView
            if (url.startsWith("http") && !url.contains("x0loq7r9a2zb3xn4k8yp6tm5wv1ucqjhf.netlify.app")) {
                // Open external links in browser
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.getContext().startActivity(intent);
                return true;
            }
            // Load internal URLs in WebView
            view.loadUrl(url);
            return true;
        }
        
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // Inject JavaScript to enhance download functionality - using regular string concatenation
            String javascriptCode = 
                "(function() {" +
                "    // Override download functions to use Android interface" +
                "    if (typeof window.Android !== 'undefined') {" +
                "        // Override conversation download" +
                "        const originalDownloadConversation = window.downloadConversation;" +
                "        if (originalDownloadConversation) {" +
                "            window.downloadConversation = function(conversationId) {" +
                "                const conversation = window.conversations.find(c => c.id === conversationId);" +
                "                if (!conversation) return;" +
                "                " +
                "                let conversationText = 'Nurvle Conversation\\\\n';" +
                "                conversationText += 'Title: ' + (conversation.title || 'Untitled') + '\\\\n';" +
                "                conversationText += 'Date: ' + new Date(conversation.timestamp).toLocaleDateString() + '\\\\n';" +
                "                conversationText += 'Time: ' + new Date(conversation.timestamp).toLocaleTimeString() + '\\\\n';" +
                "                conversationText += '='.repeat(50) + '\\\\n\\\\n';" +
                "                " +
                "                conversation.messages.forEach((message, index) => {" +
                "                    const isUser = message.role === 'user';" +
                "                    const sender = isUser ? 'USER' : 'ASSISTANT';" +
                "                    const content = message.content || '';" +
                "                    " +
                "                    if (index > 0) {" +
                "                        conversationText += '\\\\n' + '-'.repeat(50) + '\\\\n\\\\n';" +
                "                    }" +
                "                    " +
                "                    conversationText += sender + ':\\\\n';" +
                "                    conversationText += content + '\\\\n';" +
                "                    " +
                "                    if (isUser && message.files && message.files.length > 0) {" +
                "                        conversationText += '\\\\nAttachments:\\\\n';" +
                "                        message.files.forEach(file => {" +
                "                            conversationText += '- ' + file.name + ' (' + file.type + ', ' + (file.size / 1024).toFixed(2) + ' KB)\\\\n';" +
                "                        });" +
                "                    }" +
                "                });" +
                "                " +
                "                conversationText += '\\\\n' + '='.repeat(50) + '\\\\n';" +
                "                conversationText += 'End of conversation\\\\n';" +
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
                "    }" +
                "})();";
            
            view.evaluateJavascript(javascriptCode, null);
        }
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
