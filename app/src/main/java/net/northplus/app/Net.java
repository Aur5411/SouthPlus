package net.northplus.app;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 极简 HTTP 取文工具（下载用户脚本用）。 */
public final class Net {

    public static final int MAX_BYTES = 4 * 1024 * 1024;

    private Net() {
    }

    public static byte[] get(String url, String referer) {
        return get(url, referer, 15000, 30000);
    }

    /** 指定超时的版本：镜像竞速时把超时压短，避免死等。 */
    public static byte[] get(String url, String referer, int connectMs, int readMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", Const.UA);
            c.setRequestProperty("Accept", "*/*");
            if (referer != null && !referer.isEmpty()) {
                c.setRequestProperty("Referer", referer);
            }
            String cookie = android.webkit.CookieManager.getInstance().getCookie(Site.base());
            if (cookie != null && !cookie.isEmpty()) {
                c.setRequestProperty("Cookie", cookie);
            }
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                int total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) break;
                    bos.write(buf, 0, n);
                }
                return bos.toByteArray();
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static String getText(String url, String referer) {
        return getText(url, referer, 15000, 30000);
    }

    /** 只探测可达性，不读响应体。用于多站点/多源的快速连通性判断。 */
    public static boolean ping(String url, int connectMs, int readMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", Const.UA);
            c.setRequestProperty("Accept", "text/html,*/*;q=0.8");
            int code = c.getResponseCode();
            return code > 0 && code < 500;
        } catch (Throwable t) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static String getText(String url, String referer, int connectMs, int readMs) {
        byte[] b = get(url, referer, connectMs, readMs);
        if (b == null) return null;
        try {
            return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
