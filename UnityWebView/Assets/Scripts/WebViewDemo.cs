using System.Collections;
using System.Collections.Generic;
using UnityEngine;

public class WebViewDemo : MonoBehaviour
{
    private GameObject avatar;

    [SerializeField] private WebView webView;
    [SerializeField] private GameObject loadingLabel = null;
    [SerializeField] private GameObject displayButton = null;

    public void DisplayWebView()
    {
        if (webView == null)
        {
            webView = FindObjectOfType<WebView>();
        }
        else if (webView.Loaded)
        {
            webView.SetVisible(true);
        }
        else
        {
            webView.CreateWebView();
        }
    }
}
