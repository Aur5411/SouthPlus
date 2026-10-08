package net.northplus.app;

import android.graphics.Bitmap;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 轻量图片加载器。
 *
 * <p>不引第三方图库的原因：站点与外链图床常校验 Referer / Cookie，
 * 自带实现才能把这两项准确带上；同时避免额外依赖。
 */
public final class ImgLoader {

    /** 单张图最大 48MB，防止异常文件把内存打爆。 */
    private static final long MAX_BYTES = 48L * 1024 * 1024;

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(16 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    public interface Callback {
        void onSuccess(Bitmap bitmap);

        void onFail(String message);
    }

    private ImgLoader() {
    }

    public static Bitmap cached(String url) {
        return CACHE.get(url);
    }

    public static void load(final String url, final int reqW, final int reqH,
                            final Callback cb) {
        Bitmap hit = CACHE.get(url);
        if (hit != null && !hit.isRecycled()) {
            cb.onSuccess(hit);
            return;
        }
        POOL.execute(() -> {
            try {
                final byte[] data = fetch(url);
                if (data == null) {
                    main(() -> cb.onFail("下载失败"));
                    return;
                }
                final Bitmap bmp = decode(data, reqW, reqH);
                if (bmp == null) {
                    main(() -> cb.onFail("解码失败"));
                    return;
                }
                CACHE.put(url, bmp);
                main(() -> cb.onSuccess(bmp));
            } catch (Throwable t) {
                final String msg = String.valueOf(t.getMessage());
                main(() -> cb.onFail(msg));
            }
        });
    }

    /** 带 Referer / Cookie / UA 拉取原始字节。 */
    public static byte[] fetch(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", Const.UA);
            c.setRequestProperty("Referer", Site.base() + "/");
            c.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8");
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
                long total = 0;
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

    private static Bitmap decode(byte[] data, int reqW, int reqH) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, o);
            o.inSampleSize = sample(o.outWidth, o.outHeight, reqW, reqH);
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int sample(int w, int h, int reqW, int reqH) {
        if (w <= 0 || h <= 0 || reqW <= 0 || reqH <= 0) return 1;
        int s = 1;
        while ((w / (s * 2)) >= reqW && (h / (s * 2)) >= reqH) {
            s *= 2;
        }
        return s;
    }

    private static void main(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }
}
