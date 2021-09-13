// 
// Decompiled by Procyon v0.5.36
// 

package com.eungyukm.unitywebview;

import android.webkit.CookieSyncManager;
import android.webkit.CookieManager;
import java.net.URLEncoder;
import android.os.Environment;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.File;
import android.os.Parcelable;
import java.io.IOException;
import android.util.Log;
import android.view.Display;
import android.graphics.Point;
import android.graphics.Rect;
import android.content.res.Configuration;
import android.webkit.WebSettings;
import android.content.pm.ApplicationInfo;
import android.view.ViewGroup;
import android.content.pm.ResolveInfo;
import java.util.Iterator;
import java.util.Map;
import android.util.Base64;
import java.net.URL;
import java.net.HttpURLConnection;
import android.webkit.HttpAuthHandler;
import android.graphics.Bitmap;
import android.annotation.TargetApi;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceRequest;
import android.webkit.WebViewClient;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import java.util.ArrayList;
import android.content.pm.PackageManager;
import java.util.concurrent.FutureTask;
import android.content.Context;
import android.app.Activity;
import java.util.concurrent.Callable;
import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;

import android.os.Build;
import android.content.Intent;
import android.util.Pair;
import java.util.List;
import android.net.Uri;
import android.webkit.ValueCallback;
import java.util.regex.Pattern;
import java.util.Hashtable;
import android.view.ViewTreeObserver;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.app.Fragment;
import android.widget.Toast;

public class CWebViewPlugin extends Fragment
{
    private static String[] PERMISSIONS_STORAGE;
    private static final int REQUEST_CODE = 100001;
    private static FrameLayout layout;
    private WebView mWebView;
    private View mVideoView;
    private ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener;
    private CWebViewPluginInterface mWebViewPlugin;
    private int progress;
    private boolean canGoBack;
    private boolean canGoForward;
    private boolean mAlertDialogEnabled;
    private Hashtable<String, String> mCustomHeaders;
    private String mWebViewUA;
    private Pattern mAllowRegex;
    private Pattern mDenyRegex;
    private Pattern mHookRegex;
    private static final int INPUT_FILE_REQUEST_CODE = 1;
    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mFilePathCallback;
    private String mCameraPhotoPath;
    private static long instanceCount;
    private long mInstanceId;
    private boolean mPaused;
    private List<Pair<String, CWebViewPlugin>> mTransactions;
    private String mBasicAuthUserName;
    private String mBasicAuthPassword;
    
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] grantResults) {
        switch (requestCode) {
            case 100001: {
                if (grantResults[0] == 0) {
                    this.ProcessChooser(this.mFilePathCallback);
                    break;
                }
                break;
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode != 1) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            if (this.mFilePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri[] results = null;
            if (resultCode == -1) {
                if (data == null) {
                    if (this.mCameraPhotoPath != null) {
                        results = new Uri[] { Uri.parse(this.mCameraPhotoPath) };
                    }
                }
                else {
                    final String dataString = data.getDataString();
                    if (dataString == null) {
                        if (this.mCameraPhotoPath != null) {
                            results = new Uri[] { Uri.parse(this.mCameraPhotoPath) };
                        }
                    }
                    else {
                        results = new Uri[] { Uri.parse(dataString) };
                    }
                }
            }
            this.mFilePathCallback.onReceiveValue(results);
            this.mFilePathCallback = null;
        }
        else {
            if (this.mUploadMessage == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri result = null;
            if (resultCode == -1 && data != null) {
                result = data.getData();
            }
            this.mUploadMessage.onReceiveValue(result);
            this.mUploadMessage = null;
        }
    }
    
    public static boolean IsWebViewAvailable() {
        final Activity a = UnityPlayer.currentActivity;
        final FutureTask<Boolean> t = new FutureTask<Boolean>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                boolean isAvailable = false;
                try {
                    WebView webView = new WebView((Context)a);
                    if (webView != null) {
                        webView = null;
                        isAvailable = true;
                    }
                }
                catch (Exception ex) {}
                return isAvailable;
            }
        });
        a.runOnUiThread((Runnable)t);
        try {
            return t.get();
        }
        catch (Exception e) {
            return false;
        }
    }
    
    public boolean verifyStoragePermissions(final Activity activity) {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        final PackageManager pm = activity.getPackageManager();
        final int hasPerm1 = pm.checkPermission("android.permission.READ_EXTERNAL_STORAGE", activity.getPackageName());
        final int hasPerm2 = pm.checkPermission("android.permission.WRITE_EXTERNAL_STORAGE", activity.getPackageName());
        final int hasPerm3 = pm.checkPermission("android.permission.CAMERA", activity.getPackageName());
        if (hasPerm1 != 0 || hasPerm2 != 0 || hasPerm3 != 0) {
            activity.runOnUiThread((Runnable)new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        CWebViewPlugin.this.requestPermissions(CWebViewPlugin.PERMISSIONS_STORAGE, 100001);
                    }
                }
            });
            return false;
        }
        return true;
    }
    
    public boolean IsInitialized() {
        return this.mWebView != null;
    }
    
    public void Init(final String gameObject, final boolean transparent, final boolean zoom, final int androidForceDarkMode, final String ua) {
        final CWebViewPlugin self = this;
        final Activity a = UnityPlayer.currentActivity;
        ++CWebViewPlugin.instanceCount;
        this.mInstanceId = CWebViewPlugin.instanceCount;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPlugin.this.mWebView != null) {
                    return;
                }
                CWebViewPlugin.this.setRetainInstance(true);
                if (CWebViewPlugin.this.mPaused) {
                    if (CWebViewPlugin.this.mTransactions == null) {
                        CWebViewPlugin.this.mTransactions = (List<Pair<String, CWebViewPlugin>>)new ArrayList();
                    }
                    CWebViewPlugin.this.mTransactions.add(Pair.create("add", self));
                }
                else {
                    a.getFragmentManager().beginTransaction().add(0, (Fragment)self, "CWebViewPlugin" + CWebViewPlugin.this.mInstanceId).commit();
                }
                CWebViewPlugin.this.mAlertDialogEnabled = true;
                CWebViewPlugin.this.mCustomHeaders = (Hashtable<String, String>)new Hashtable();
                final WebView webView = new WebView((Context)a);
                if (Build.VERSION.SDK_INT >= 19) {
                    try {
                        final ApplicationInfo ai = a.getPackageManager().getApplicationInfo(a.getPackageName(), 0);
                        if ((ai.flags & 0x2) != 0x0) {
                            WebView.setWebContentsDebuggingEnabled(true);
                        }
                    }
                    catch (Exception ex) {}
                }
                webView.setVisibility(WebView.GONE);
                webView.setFocusable(true);
                webView.setFocusableInTouchMode(true);
                webView.getSettings().setUserAgentString("Android WebView");
                webView.setWebChromeClient((WebChromeClient)new WebChromeClient() {
                    public void onPermissionRequest(final PermissionRequest request) {
                        final String[] resources;
                        final String[] requestedResources = resources = request.getResources();
                        for (final String r : resources) {
                            if (r.equals("android.webkit.resource.VIDEO_CAPTURE") || r.equals("android.webkit.resource.AUDIO_CAPTURE")) {
                                request.grant(requestedResources);
                                break;
                            }
                        }
                    }
                    
                    public void onProgressChanged(final WebView view, final int newProgress) {
                        CWebViewPlugin.this.progress = newProgress;
                    }
                    
                    public void onShowCustomView(final View view, final WebChromeClient.CustomViewCallback callback) {
                        super.onShowCustomView(view, callback);
                        if (CWebViewPlugin.layout != null) {
                            CWebViewPlugin.this.mVideoView = view;
                            CWebViewPlugin.layout.setBackgroundColor(-16777216);
                            CWebViewPlugin.layout.addView(CWebViewPlugin.this.mVideoView);
                        }
                    }
                    
                    public void onHideCustomView() {
                        super.onHideCustomView();
                        if (CWebViewPlugin.layout != null) {
                            CWebViewPlugin.layout.removeView(CWebViewPlugin.this.mVideoView);
                            CWebViewPlugin.layout.setBackgroundColor(0);
                            CWebViewPlugin.this.mVideoView = null;
                        }
                    }
                    
                    public boolean onJsAlert(final WebView view, final String url, final String message, final JsResult result) {
                        if (!CWebViewPlugin.this.mAlertDialogEnabled) {
                            result.cancel();
                            return true;
                        }
                        return super.onJsAlert(view, url, message, result);
                    }
                    
                    public boolean onJsConfirm(final WebView view, final String url, final String message, final JsResult result) {
                        if (!CWebViewPlugin.this.mAlertDialogEnabled) {
                            result.cancel();
                            return true;
                        }
                        return super.onJsConfirm(view, url, message, result);
                    }
                    
                    public boolean onJsPrompt(final WebView view, final String url, final String message, final String defaultValue, final JsPromptResult result) {
                        if (!CWebViewPlugin.this.mAlertDialogEnabled) {
                            result.cancel();
                            return true;
                        }
                        return super.onJsPrompt(view, url, message, defaultValue, result);
                    }
                    
                    public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
                        callback.invoke(origin, true, false);
                    }
                    
                    public void openFileChooser(final ValueCallback<Uri> uploadFile, final String acceptType) {
                        this.openFileChooser(uploadFile, acceptType, "");
                    }
                    
                    public void openFileChooser(final ValueCallback<Uri> uploadFile, final String acceptType, final String capture) {
                        if (CWebViewPlugin.this.mUploadMessage != null) {
                            CWebViewPlugin.this.mUploadMessage.onReceiveValue(null);
                        }
                        CWebViewPlugin.this.mUploadMessage = uploadFile;
                        final Intent intent = new Intent("android.intent.action.GET_CONTENT");
                        intent.addCategory("android.intent.category.OPENABLE");
                        intent.setType("*/*");
                        CWebViewPlugin.this.startActivityForResult(intent, 1);
                    }
                    
                    public boolean onShowFileChooser(final WebView webView, final ValueCallback<Uri[]> filePathCallback, final WebChromeClient.FileChooserParams fileChooserParams) {
                        if (CWebViewPlugin.this.mFilePathCallback != null) {
                            CWebViewPlugin.this.mFilePathCallback.onReceiveValue(null);
                        }
                        CWebViewPlugin.this.mFilePathCallback = filePathCallback;
                        if (!CWebViewPlugin.this.verifyStoragePermissions(a)) {
                            return true;
                        }
                        CWebViewPlugin.this.ProcessChooser(CWebViewPlugin.this.mFilePathCallback);
                        return true;
                    }
                });
                CWebViewPlugin.this.mWebViewPlugin = new CWebViewPluginInterface(self, gameObject);
                webView.setWebViewClient((WebViewClient)new WebViewClient() {
                    public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
                        webView.loadUrl("about:blank");
                        CWebViewPlugin.this.canGoBack = webView.canGoBack();
                        CWebViewPlugin.this.canGoForward = webView.canGoForward();
                        CWebViewPlugin.this.mWebViewPlugin.call("CallOnError", errorCode + "\t" + description + "\t" + failingUrl);
                    }
                    
                    @TargetApi(21)
                    public void onReceivedHttpError(final WebView view, final WebResourceRequest request, final WebResourceResponse errorResponse) {
                        CWebViewPlugin.this.canGoBack = webView.canGoBack();
                        CWebViewPlugin.this.canGoForward = webView.canGoForward();
                        CWebViewPlugin.this.mWebViewPlugin.call("CallOnHttpError", Integer.toString(errorResponse.getStatusCode()));
                    }
                    
                    public void onPageStarted(final WebView view, final String url, final Bitmap favicon) {
                        CWebViewPlugin.this.canGoBack = webView.canGoBack();
                        CWebViewPlugin.this.canGoForward = webView.canGoForward();
                        CWebViewPlugin.this.mWebViewPlugin.call("CallOnStarted", url);
                    }
                    
                    public void onPageFinished(final WebView view, final String url) {
                        CWebViewPlugin.this.canGoBack = webView.canGoBack();
                        CWebViewPlugin.this.canGoForward = webView.canGoForward();
                        CWebViewPlugin.this.mWebViewPlugin.call("CallOnLoaded", url);
                    }
                    
                    public void onLoadResource(final WebView view, final String url) {
                        CWebViewPlugin.this.canGoBack = webView.canGoBack();
                        CWebViewPlugin.this.canGoForward = webView.canGoForward();
                    }
                    
                    public void onReceivedHttpAuthRequest(final WebView view, final HttpAuthHandler handler, final String host, final String realm) {
                        if (CWebViewPlugin.this.mBasicAuthUserName != null && CWebViewPlugin.this.mBasicAuthPassword != null) {
                            handler.proceed(CWebViewPlugin.this.mBasicAuthUserName, CWebViewPlugin.this.mBasicAuthPassword);
                        }
                        else {
                            handler.cancel();
                        }
                    }
                    
                    public WebResourceResponse shouldInterceptRequest(final WebView view, final String url) {
                        if (CWebViewPlugin.this.mCustomHeaders == null || CWebViewPlugin.this.mCustomHeaders.isEmpty()) {
                            return super.shouldInterceptRequest(view, url);
                        }
                        try {
                            final HttpURLConnection urlCon = (HttpURLConnection)new URL(url).openConnection();
                            urlCon.setInstanceFollowRedirects(false);
                            urlCon.setRequestProperty("User-Agent", CWebViewPlugin.this.mWebViewUA);
                            if (CWebViewPlugin.this.mBasicAuthUserName != null && CWebViewPlugin.this.mBasicAuthPassword != null) {
                                final String authorization = CWebViewPlugin.this.mBasicAuthUserName + ":" + CWebViewPlugin.this.mBasicAuthPassword;
                                urlCon.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(authorization.getBytes(), 2));
                            }
                            if (Build.VERSION.SDK_INT != 19 && Build.VERSION.SDK_INT != 20) {
                                final String cookies = CWebViewPlugin.this.GetCookies(url);
                                if (cookies != null && !cookies.isEmpty()) {
                                    urlCon.addRequestProperty("Cookie", cookies);
                                }
                            }
                            for (final Map.Entry<String, String> entry : CWebViewPlugin.this.mCustomHeaders.entrySet()) {
                                urlCon.setRequestProperty(entry.getKey(), entry.getValue());
                            }
                            urlCon.connect();
                            final int responseCode = urlCon.getResponseCode();
                            if (responseCode >= 300 && responseCode < 400) {
                                return null;
                            }
                            final List<String> setCookieHeaders = urlCon.getHeaderFields().get("Set-Cookie");
                            if (setCookieHeaders != null) {
                                if (Build.VERSION.SDK_INT == 19 || Build.VERSION.SDK_INT == 20) {
                                    UnityPlayer.currentActivity.runOnUiThread((Runnable)new Runnable() {
                                        @Override
                                        public void run() {
                                            CWebViewPlugin.this.SetCookies(url, setCookieHeaders);
                                        }
                                    });
                                }
                                else {
                                    CWebViewPlugin.this.SetCookies(url, setCookieHeaders);
                                }
                            }
                            return new WebResourceResponse(urlCon.getContentType().split(";", 2)[0], urlCon.getContentEncoding(), urlCon.getInputStream());
                        }
                        catch (Exception e) {
                            return super.shouldInterceptRequest(view, url);
                        }
                    }
                    
                    public boolean shouldOverrideUrlLoading(final WebView view, final String url) {
                        CWebViewPlugin.this.canGoBack = webView.canGoBack();
                        CWebViewPlugin.this.canGoForward = webView.canGoForward();
                        boolean pass = true;
                        if (CWebViewPlugin.this.mAllowRegex != null && CWebViewPlugin.this.mAllowRegex.matcher(url).find()) {
                            pass = true;
                        }
                        else if (CWebViewPlugin.this.mDenyRegex != null && CWebViewPlugin.this.mDenyRegex.matcher(url).find()) {
                            pass = false;
                        }
                        if (!pass) {
                            return true;
                        }
                        if (url.startsWith("unity:")) {
                            final String message = url.substring(6);
                            CWebViewPlugin.this.mWebViewPlugin.call("CallFromJS", message);
                            return true;
                        }
                        if (CWebViewPlugin.this.mHookRegex != null && CWebViewPlugin.this.mHookRegex.matcher(url).find()) {
                            CWebViewPlugin.this.mWebViewPlugin.call("CallOnHooked", url);
                            return true;
                        }
                        if (!url.toLowerCase().endsWith(".pdf") && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://") || url.startsWith("javascript:"))) {
                            CWebViewPlugin.this.mWebViewPlugin.call("CallOnStarted", url);
                            return false;
                        }
                        final Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(url));
                        final PackageManager pm = a.getPackageManager();
                        final List<ResolveInfo> apps = (List<ResolveInfo>)pm.queryIntentActivities(intent, 0);
                        if (apps.size() > 0) {
                            view.getContext().startActivity(intent);
                        }
                        return true;
                    }
                });
                webView.addJavascriptInterface(CWebViewPlugin.this.mWebViewPlugin, "Unity");
                final WebSettings webSettings = webView.getSettings();
                if (ua != null && ua.length() > 0) {
                    webSettings.setUserAgentString(ua);
                }
                CWebViewPlugin.this.mWebViewUA = webSettings.getUserAgentString();
                if (zoom) {
                    webSettings.setSupportZoom(true);
                    webSettings.setBuiltInZoomControls(true);
                }
                else {
                    webSettings.setSupportZoom(false);
                    webSettings.setBuiltInZoomControls(false);
                }
                webSettings.setDisplayZoomControls(false);
                webSettings.setLoadWithOverviewMode(true);
                webSettings.setUseWideViewPort(true);
                webSettings.setJavaScriptEnabled(true);
                webSettings.setGeolocationEnabled(true);
                if (Build.VERSION.SDK_INT >= 16) {
                    webSettings.setAllowUniversalAccessFromFileURLs(true);
                }
                if (Build.VERSION.SDK_INT >= 17) {
                    webSettings.setMediaPlaybackRequiresUserGesture(false);
                }
                webSettings.setDatabaseEnabled(true);
                webSettings.setDomStorageEnabled(true);
                final String databasePath = webView.getContext().getDir("databases", 0).getPath();
                webSettings.setDatabasePath(databasePath);
                webSettings.setAllowFileAccess(true);
                if (Build.VERSION.SDK_INT >= 29) {
                    switch (androidForceDarkMode) {
                        case 0: {
                            final Configuration configuration = UnityPlayer.currentActivity.getResources().getConfiguration();
                            switch (configuration.uiMode & 0x30) {
                                case 16: {
                                    webSettings.setForceDark(WebSettings.FORCE_DARK_OFF);
                                    break;
                                }
                                case 32: {
                                    webSettings.setForceDark(WebSettings.FORCE_DARK_ON);
                                    break;
                                }
                            }
                            break;
                        }
                        case 1: {
                            webSettings.setForceDark(WebSettings.FORCE_DARK_OFF);
                            break;
                        }
                        case 2: {
                            webSettings.setForceDark(WebSettings.FORCE_DARK_ON);
                            break;
                        }
                    }
                }
                if (transparent) {
                    webView.setBackgroundColor(0);
                }
                if (CWebViewPlugin.layout == null || CWebViewPlugin.layout.getParent() != a.findViewById(UnityPlayerActivity.CONTEXT_INCLUDE_CODE)) {
                    CWebViewPlugin.layout = new FrameLayout((Context)a);
                    a.addContentView((View)CWebViewPlugin.layout, new ViewGroup.LayoutParams(-1, -1));
                    CWebViewPlugin.layout.setFocusable(true);
                    CWebViewPlugin.layout.setFocusableInTouchMode(true);
                }
                CWebViewPlugin.layout.addView((View)webView, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1, 0));
                CWebViewPlugin.this.mWebView = webView;
            }
        });
        final View activityRootView = a.getWindow().getDecorView().getRootView();
        this.mGlobalLayoutListener = (ViewTreeObserver.OnGlobalLayoutListener)new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                final Rect r = new Rect();
                activityRootView.getWindowVisibleDisplayFrame(r);
                final Display display = a.getWindowManager().getDefaultDisplay();
                int h = 0;
                try {
                    final Point size = new Point();
                    display.getSize(size);
                    h = size.y;
                }
                catch (NoSuchMethodError err) {
                    h = display.getHeight();
                }
                final int heightDiff = activityRootView.getRootView().getHeight() - (r.bottom - r.top);
                if (heightDiff > h / 3) {
                    UnityPlayer.UnitySendMessage(gameObject, "SetKeyboardVisible", "true");
                }
                else {
                    UnityPlayer.UnitySendMessage(gameObject, "SetKeyboardVisible", "false");
                }
            }
        };
        activityRootView.getViewTreeObserver().addOnGlobalLayoutListener(this.mGlobalLayoutListener);
    }
    
    private void ProcessChooser(final ValueCallback<Uri[]> filePath) {
        this.mCameraPhotoPath = null;
        Intent takePictureIntent = new Intent("android.media.action.IMAGE_CAPTURE");
        if (takePictureIntent.resolveActivity(this.getActivity().getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = this.createImageFile();
            }
            catch (IOException ex) {
                Log.e("CWebViewPlugin", "Unable to create Image File", (Throwable)ex);
            }
            if (photoFile != null) {
                takePictureIntent.putExtra("PhotoPath", this.mCameraPhotoPath = "file:" + photoFile.getAbsolutePath());
                takePictureIntent.putExtra("output", (Parcelable)Uri.fromFile(photoFile));
                takePictureIntent.putExtra("android.intent.extra.sizeLimit", "720000");
            }
            else {
                takePictureIntent = null;
            }
        }
        final Intent contentSelectionIntent = new Intent("android.intent.action.GET_CONTENT");
        contentSelectionIntent.addCategory("android.intent.category.OPENABLE");
        contentSelectionIntent.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
        contentSelectionIntent.setType("image/*");
        Intent[] intentArray;
        if (takePictureIntent != null) {
            intentArray = new Intent[] { takePictureIntent };
        }
        else {
            intentArray = new Intent[0];
        }
        final Intent chooserIntent = new Intent("android.intent.action.CHOOSER");
        chooserIntent.putExtra("android.intent.extra.INTENT", (Parcelable)contentSelectionIntent);
        chooserIntent.putExtra("android.intent.extra.INITIAL_INTENTS", (Parcelable[])intentArray);
        this.startActivityForResult(Intent.createChooser(chooserIntent, (CharSequence)"Select images"), 1);
    }
    
    private File createImageFile() throws IOException {
        final String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        final String imageFileName = "JPEG_" + timeStamp + "_";
        final File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        final File imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);
        return imageFile;
    }
    
    public void Destroy() {
        final Activity a = UnityPlayer.currentActivity;
        final CWebViewPlugin self = this;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPlugin.this.mWebView == null) {
                    return;
                }
                if (CWebViewPlugin.this.mGlobalLayoutListener != null) {
                    final View activityRootView = a.getWindow().getDecorView().getRootView();
                    activityRootView.getViewTreeObserver().removeOnGlobalLayoutListener(CWebViewPlugin.this.mGlobalLayoutListener);
                    CWebViewPlugin.this.mGlobalLayoutListener = null;
                }
                CWebViewPlugin.this.mWebView.stopLoading();
                if (CWebViewPlugin.this.mVideoView != null) {
                    CWebViewPlugin.layout.removeView(CWebViewPlugin.this.mVideoView);
                    CWebViewPlugin.layout.setBackgroundColor(0);
                    CWebViewPlugin.this.mVideoView = null;
                }
                CWebViewPlugin.layout.removeView((View)CWebViewPlugin.this.mWebView);
                CWebViewPlugin.this.mWebView.destroy();
                CWebViewPlugin.this.mWebView = null;
                if (CWebViewPlugin.this.mPaused) {
                    if (CWebViewPlugin.this.mTransactions == null) {
                        CWebViewPlugin.this.mTransactions = (List<Pair<String, CWebViewPlugin>>)new ArrayList();
                    }
                    CWebViewPlugin.this.mTransactions.add(Pair.create("remove", self));
                }
                else {
                    a.getFragmentManager().beginTransaction().remove((Fragment)self).commit();
                }
            }
        });
    }
    
    public boolean SetURLPattern(final String allowPattern, final String denyPattern, final String hookPattern) {
        try {
            final Pattern allow = (allowPattern == null || allowPattern.length() == 0) ? null : Pattern.compile(allowPattern);
            final Pattern deny = (denyPattern == null || denyPattern.length() == 0) ? null : Pattern.compile(denyPattern);
            final Pattern hook = (hookPattern == null || hookPattern.length() == 0) ? null : Pattern.compile(hookPattern);
            final Activity a = UnityPlayer.currentActivity;
            a.runOnUiThread((Runnable)new Runnable() {
                @Override
                public void run() {
                    CWebViewPlugin.this.mAllowRegex = allow;
                    CWebViewPlugin.this.mDenyRegex = deny;
                    CWebViewPlugin.this.mHookRegex = hook;
                }
            });
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }
    
    public void LoadURL(final String url) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPlugin.this.mWebView == null) {
                    Toast toast = Toast.makeText(a, "CWebViewPlugin is null", Toast.LENGTH_SHORT);
                    toast.show();
                    return;
                }
                if (CWebViewPlugin.this.mCustomHeaders != null && !CWebViewPlugin.this.mCustomHeaders.isEmpty()) {
                    Toast toast = Toast.makeText(a, "CWebViewPlugin.this.mCustomHeaders is null", Toast.LENGTH_SHORT);
                    toast.show();
                    CWebViewPlugin.this.mWebView.loadUrl(url, (Map)CWebViewPlugin.this.mCustomHeaders);
                }
                else {
                    Toast toast = Toast.makeText(a, "CWebViewPlugin Stable", Toast.LENGTH_SHORT);
                    toast.show();
                    CWebViewPlugin.this.mWebView.loadUrl(url);
                }
            }
        });
    }
    
    public void LoadHTML(final String html, final String baseURL) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPlugin.this.mWebView == null) {
                    return;
                }
                CWebViewPlugin.this.mWebView.loadDataWithBaseURL(baseURL, html, "text/html", "UTF8", (String)null);
            }
        });
    }
    
    public void EvaluateJS(final String js) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPlugin.this.mWebView == null) {
                    return;
                }
                if (Build.VERSION.SDK_INT >= 19) {
                    CWebViewPlugin.this.mWebView.evaluateJavascript(js, (ValueCallback)null);
                }
                else {
                    CWebViewPlugin.this.mWebView.loadUrl("javascript:" + URLEncoder.encode(js));
                }
            }
        });
    }
    
    public void GoBack() {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPlugin.this.mWebView == null) {
                    return;
                }
                CWebViewPlugin.this.mWebView.goBack();
            }
        });
    }
    
    public void GoForward() {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPlugin.this.mWebView == null) {
                    return;
                }
                CWebViewPlugin.this.mWebView.goForward();
            }
        });
    }
    
    public void Reload() {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPlugin.this.mWebView == null) {
                    return;
                }
                CWebViewPlugin.this.mWebView.reload();
            }
        });
    }
    
    public void SetMargins(final int left, final int top, final int right, final int bottom) {
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1, 0);
        params.setMargins(left, top, right, bottom);
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPlugin.this.mWebView == null) {
                    return;
                }
                CWebViewPlugin.this.mWebView.setLayoutParams((ViewGroup.LayoutParams)params);
            }
        });
    }
    
    public void SetVisibility(final boolean visibility) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPlugin.this.mWebView == null) {
                    return;
                }
                if (visibility) {
                    CWebViewPlugin.this.mWebView.setVisibility(View.VISIBLE);
                    CWebViewPlugin.layout.requestFocus();
                    CWebViewPlugin.this.mWebView.requestFocus();
                }
                else {
                    CWebViewPlugin.this.mWebView.setVisibility(View.GONE);
                }
            }
        });
    }
    
    public void SetAlertDialogEnabled(final boolean enabled) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                CWebViewPlugin.this.mAlertDialogEnabled = enabled;
            }
        });
    }
    
    public void OnApplicationPause(final boolean paused) {
        this.mPaused = paused;
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (!CWebViewPlugin.this.mPaused && CWebViewPlugin.this.mTransactions != null) {
                    for (final Pair<String, CWebViewPlugin> pair : CWebViewPlugin.this.mTransactions) {
                        final CWebViewPlugin self = (CWebViewPlugin)pair.second;
                        final String s = (String)pair.first;
                        switch (s) {
                            case "add": {
                                a.getFragmentManager().beginTransaction().add(0, (Fragment)self, "CWebViewPlugin" + CWebViewPlugin.this.mInstanceId).commit();
                                continue;
                            }
                            case "remove": {
                                a.getFragmentManager().beginTransaction().remove((Fragment)self).commit();
                                continue;
                            }
                        }
                    }
                    CWebViewPlugin.this.mTransactions.clear();
                }
                if (CWebViewPlugin.this.mWebView == null) {
                    return;
                }
                if (CWebViewPlugin.this.mPaused) {
                    CWebViewPlugin.this.mWebView.onPause();
                    if (CWebViewPlugin.this.mWebView.getVisibility() == View.VISIBLE) {
                        CWebViewPlugin.this.mWebView.pauseTimers();
                    }
                }
                else {
                    CWebViewPlugin.this.mWebView.onResume();
                    CWebViewPlugin.this.mWebView.resumeTimers();
                }
            }
        });
    }
    
    public void AddCustomHeader(final String headerKey, final String headerValue) {
        if (this.mCustomHeaders == null) {
            return;
        }
        this.mCustomHeaders.put(headerKey, headerValue);
    }
    
    public String GetCustomHeaderValue(final String headerKey) {
        if (this.mCustomHeaders == null) {
            return null;
        }
        if (!this.mCustomHeaders.containsKey(headerKey)) {
            return null;
        }
        return this.mCustomHeaders.get(headerKey);
    }
    
    public void RemoveCustomHeader(final String headerKey) {
        if (this.mCustomHeaders == null) {
            return;
        }
        if (this.mCustomHeaders.containsKey(headerKey)) {
            this.mCustomHeaders.remove(headerKey);
        }
    }
    
    public void ClearCustomHeader() {
        if (this.mCustomHeaders == null) {
            return;
        }
        this.mCustomHeaders.clear();
    }
    
    public void ClearCookies() {
        if (Build.VERSION.SDK_INT >= 21) {
            CookieManager.getInstance().removeAllCookies((ValueCallback)null);
            CookieManager.getInstance().flush();
        }
        else {
            final Activity a = UnityPlayer.currentActivity;
            final CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance((Context)a);
            cookieSyncManager.startSync();
            final CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookie();
            cookieManager.removeSessionCookie();
            cookieSyncManager.stopSync();
            cookieSyncManager.sync();
        }
    }
    
    public void SaveCookies() {
        if (Build.VERSION.SDK_INT >= 21) {
            CookieManager.getInstance().flush();
        }
        else {
            final Activity a = UnityPlayer.currentActivity;
            final CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance((Context)a);
            cookieSyncManager.startSync();
            cookieSyncManager.stopSync();
            cookieSyncManager.sync();
        }
    }
    
    public String GetCookies(final String url) {
        final CookieManager cookieManager = CookieManager.getInstance();
        return cookieManager.getCookie(url);
    }
    
    public void SetCookies(final String url, final List<String> setCookieHeaders) {
        if (Build.VERSION.SDK_INT >= 21) {
            final CookieManager cookieManager = CookieManager.getInstance();
            for (final String header : setCookieHeaders) {
                cookieManager.setCookie(url, header);
            }
            cookieManager.flush();
        }
        else {
            final Activity a = UnityPlayer.currentActivity;
            final CookieSyncManager cookieSyncManager = CookieSyncManager.createInstance((Context)a);
            cookieSyncManager.startSync();
            final CookieManager cookieManager2 = CookieManager.getInstance();
            for (final String header2 : setCookieHeaders) {
                cookieManager2.setCookie(url, header2);
            }
            cookieSyncManager.stopSync();
            cookieSyncManager.sync();
        }
    }
    
    public void SetBasicAuthInfo(final String userName, final String password) {
        this.mBasicAuthUserName = userName;
        this.mBasicAuthPassword = password;
    }
    
    public void ClearCache(final boolean includeDiskFiles) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPlugin.this.mWebView == null) {
                    return;
                }
                CWebViewPlugin.this.mWebView.clearCache(includeDiskFiles);
            }
        });
    }
    
    static {
        CWebViewPlugin.PERMISSIONS_STORAGE = new String[] { "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.CAMERA" };
        CWebViewPlugin.layout = null;
    }
}
