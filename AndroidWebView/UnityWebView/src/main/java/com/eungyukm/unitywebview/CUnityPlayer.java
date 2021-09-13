// 
// Decompiled by Procyon v0.5.36
// 

package com.eungyukm.unitywebview;

import android.view.SurfaceView;
import android.view.View;
import android.content.Context;
import android.content.ContextWrapper;
import com.unity3d.player.UnityPlayer;

public class CUnityPlayer extends UnityPlayer
{
    public CUnityPlayer(final ContextWrapper contextwrapper) {
        super((Context)contextwrapper);
    }
    
    public void addView(final View child) {
        if (child instanceof SurfaceView) {
            ((SurfaceView)child).setZOrderOnTop(false);
        }
        super.addView(child);
    }
}
