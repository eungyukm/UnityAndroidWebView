// 
// Decompiled by Procyon v0.5.36
// 

package com.eungyukm.unitywebview;

import android.app.Activity;
import com.unity3d.player.UnityPlayer;
import android.webkit.JavascriptInterface;

class CWebViewPluginInterface
{
    private CWebViewPlugin mPlugin;
    private String mGameObject;
    
    public CWebViewPluginInterface(final CWebViewPlugin plugin, final String gameObject) {
        this.mPlugin = plugin;
        this.mGameObject = gameObject;
    }
    
    @JavascriptInterface
    public void call(final String message) {
        this.call("CallFromJS", message);
    }
    
    public void call(final String method, final String message) {
        final Activity a = UnityPlayer.currentActivity;
        a.runOnUiThread((Runnable)new Runnable() {
            @Override
            public void run() {
                if (CWebViewPluginInterface.this.mPlugin.IsInitialized()) {
                    UnityPlayer.UnitySendMessage(CWebViewPluginInterface.this.mGameObject, method, message);
                }
            }
        });
    }
}
