package net.northplus.app;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** 把图片写进系统相册。 */
public final class Gallery {

    private Gallery() {
    }

    /** 返回落盘后的文件名。 */
    public static String save(Context ctx, String url, byte[] raw) throws IOException {
        if (raw == null || raw.length == 0) throw new IOException("图片数据为空");
        String name = fileName(url);
        String mime = mimeOf(name);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Images.Media.MIME_TYPE, mime);
            cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/NorthPlus");
            cv.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = ctx.getContentResolver()
                    .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new IOException("无法创建媒体记录");
            try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException("无法写入媒体文件");
                os.write(raw);
                os.flush();
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, done, null, null);
            return name;
        }

        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "NorthPlus");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建目录");
        File f = new File(dir, name);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(raw);
            fos.flush();
        }
        MediaScannerConnection.scanFile(ctx, new String[]{f.getAbsolutePath()},
                new String[]{mime}, null);
        return name;
    }

    public static String fileName(String url) {
        String base = null;
        try {
            String path = Uri.parse(url).getPath();
            if (path != null) base = path.substring(path.lastIndexOf('/') + 1);
        } catch (Exception ignored) {
        }
        if (base == null || base.isEmpty()) base = "";
        int q = base.indexOf('?');
        if (q > 0) base = base.substring(0, q);
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isEmpty() || base.startsWith(".") || base.length() < 5) {
            base = "np_" + System.currentTimeMillis() + ".jpg";
        }
        int dot = base.lastIndexOf('.');
        if (dot <= 0) {
            base = base + ".jpg";
        } else if (base.length() - dot > 6) {
            base = base.substring(0, dot) + ".jpg";
        }
        if (base.length() > 80) {
            int d = base.lastIndexOf('.');
            base = base.substring(0, 60) + base.substring(d);
        }
        return base;
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }
}
