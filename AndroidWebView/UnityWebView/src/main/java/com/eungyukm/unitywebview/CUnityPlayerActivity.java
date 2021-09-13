// 
// Decompiled by Procyon v0.5.36
// 

package com.eungyukm.unitywebview;

import android.view.View;
import android.content.ContextWrapper;
import android.os.Bundle;
import com.unity3d.player.UnityPlayerActivity;

public class CUnityPlayerActivity extends UnityPlayerActivity
{
    public void onCreate(final Bundle bundle) {
        this.requestWindowFeature(1);
        super.onCreate(bundle);
        this.getWindow().setFormat(2);
        this.setContentView((View)(this.mUnityPlayer = new CUnityPlayer((ContextWrapper)this)));
        this.mUnityPlayer.requestFocus();
    }
}
