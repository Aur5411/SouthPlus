package net.northplus.app;

import android.webkit.JavascriptInterface;

/** 网页 → 原生 的桥。 */
public class WebBridge {

    public interface Host {
        /** 网页里点了图片：imagesJson 是 JSON 字符串数组，index 是当前下标。 */
        void onOpenImages(String imagesJson, int index);

        /** 网页里请求用原生方式打开某个地址。 */
        void onOpenUrl(String url);

        void onToast(String msg);
    }

    private final Host host;

    public WebBridge(Host host) {
        this.host = host;
    }

    @JavascriptInterface
    public void openImage(String imagesJson, int index) {
        host.onOpenImages(imagesJson, index);
    }

    @JavascriptInterface
    public void openUrl(String url) {
        host.onOpenUrl(url);
    }

    @JavascriptInterface
    public void toast(String msg) {
        host.onToast(msg);
    }
}
