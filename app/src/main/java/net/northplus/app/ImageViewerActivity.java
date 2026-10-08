package net.northplus.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 帖子内图片的全屏查看器：双指缩放、左右滑动切换、保存到相册。 */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_LIST = "np_images";
    public static final String EXTRA_INDEX = "np_index";

    private static final int REQ_WRITE = 0x71;

    private final ArrayList<String> list = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private int index = 0;

    private ZoomImageView image;
    private ProgressBar loading;
    private TextView counter;
    private View topBar;
    private View bottomBar;

    private boolean barsVisible = true;
    private String pendingSaveUrl;
    private final Runnable hideBars = () -> setBarsVisible(false);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent it = getIntent();
        ArrayList<String> src = it == null
                ? null : it.getStringArrayListExtra(EXTRA_LIST);
        if (src != null) list.addAll(src);
        index = it == null ? 0 : it.getIntExtra(EXTRA_INDEX, 0);
        if (index < 0) index = 0;
        if (index >= list.size()) index = Math.max(0, list.size() - 1);
        if (list.isEmpty()) {
            finish();
            return;
        }

        image = findViewById(R.id.zoomImage);
        loading = findViewById(R.id.viewerProgress);
        counter = findViewById(R.id.viewerCounter);
        topBar = findViewById(R.id.viewerTop);
        bottomBar = findViewById(R.id.viewerBottom);

        findViewById(R.id.viewerClose).setOnClickListener(v -> finish());
        findViewById(R.id.viewerOpen).setOnClickListener(v -> openInBrowser());
        findViewById(R.id.viewerSave).setOnClickListener(v -> saveCurrent());
        findViewById(R.id.viewerShare).setOnClickListener(v -> copyUrl());

        image.setSwipeListener(delta -> {
            int next = index + delta;
            if (next < 0 || next >= list.size()) {
                toast(delta > 0 ? "已经是最后一张" : "已经是第一张");
                return;
            }
            index = next;
            loadCurrent();
        });
        image.setTapListener(this::toggleBars);

        loadCurrent();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(hideBars);
        io.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ 加载

    private void loadCurrent() {
        final String url = list.get(index);
        counter.setText(getString(R.string.img_count, index + 1, list.size()));
        Bitmap cached = ImgLoader.cached(url);
        if (cached != null) {
            image.setImageBitmap(cached);
            loading.setVisibility(View.GONE);
            showBarsTemporarily();
            return;
        }
        image.setImageDrawable(null);
        loading.setVisibility(View.VISIBLE);
        int reqW = getResources().getDisplayMetrics().widthPixels * 2;
        int reqH = getResources().getDisplayMetrics().heightPixels * 2;
        ImgLoader.load(url, reqW, reqH, new ImgLoader.Callback() {
            @Override
            public void onSuccess(Bitmap bitmap) {
                if (isFinishing() || isDestroyed()) return;
                if (!url.equals(list.get(index))) return;   // 用户已经滑走了
                image.setImageBitmap(bitmap);
                loading.setVisibility(View.GONE);
                showBarsTemporarily();
            }

            @Override
            public void onFail(String message) {
                if (isFinishing() || isDestroyed()) return;
                loading.setVisibility(View.GONE);
                toast("图片加载失败" + (message == null ? "" : "：" + message));
            }
        });
    }

    // ------------------------------------------------------------------ 操作

    private void openInBrowser() {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(list.get(index)));
            startActivity(i);
        } catch (Exception e) {
            toast("没有可打开该链接的应用");
        }
    }

    private void copyUrl() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("url", list.get(index)));
            toast(getString(R.string.link_copied));
        }
    }

    private void saveCurrent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingSaveUrl = list.get(index);
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE);
            return;
        }
        final String url = list.get(index);
        toast("正在保存…");
        io.execute(() -> {
            byte[] raw = ImgLoader.fetch(url);
            String msg;
            boolean ok = false;
            if (raw == null) {
                msg = getString(R.string.save_failed);
            } else {
                try {
                    String name = Gallery.save(this, url, raw);
                    msg = getString(R.string.saved) + "：" + name;
                    ok = true;
                } catch (Exception e) {
                    msg = getString(R.string.save_failed) + "：" + e.getMessage();
                }
            }
            final String fmsg = msg;
            ui.post(() -> {
                if (!isFinishing()) Toast.makeText(this, fmsg, Toast.LENGTH_SHORT).show();
            });
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_WRITE) return;
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            saveCurrent();
        } else {
            toast(getString(R.string.no_permission));
        }
        pendingSaveUrl = null;
    }

    // ------------------------------------------------------------------ 顶/底栏

    private void toggleBars() {
        setBarsVisible(!barsVisible);
    }

    private void setBarsVisible(boolean visible) {
        barsVisible = visible;
        float to = visible ? 0f : -topBar.getHeight();
        topBar.animate().translationY(visible ? 0f : to).setDuration(160).start();
        bottomBar.animate().translationY(visible ? 0f : bottomBar.getHeight())
                .setDuration(160).start();
        topBar.setClickable(!visible);
        ui.removeCallbacks(hideBars);
    }

    private void showBarsTemporarily() {
        setBarsVisible(true);
        ui.removeCallbacks(hideBars);
        ui.postDelayed(hideBars, 2600);
    }

    private void toast(String msg) {
        if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
