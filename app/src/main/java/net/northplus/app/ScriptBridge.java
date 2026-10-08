package net.northplus.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

import androidx.core.app.NotificationCompat;

import java.lang.ref.WeakReference;

/**
 * 暴露给网页的 {@code NP_SCRIPT} 桥，供用户脚本宿主（host.js）与脚本本身使用。
 *
 * <p>注意：{@code @JavascriptInterface} 方法由 WebView 在 JS 线程同步调用，
 * 因此这里只做内存操作或转投主线程，不要做长耗时任务。
 */
public class ScriptBridge {

    private static final String CHANNEL_ID = "np_scripts";
    private static final int NOTIFY_ID = 0x4E50;

    private final Context app;
    private final UserscriptStore store;
    private final WeakReference<BaseWebActivity> hostRef;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public ScriptBridge(BaseWebActivity host) {
        this.hostRef = new WeakReference<>(host);
        this.app = host.getApplicationContext();
        this.store = UserscriptStore.get(app);
    }

    // ---------------------------------------------------------------- 脚本调度

    /** 当前 URL 匹配到的启用脚本（含源码）。 */
    @JavascriptInterface
    public String listFor(String url) {
        return store.jsonFor(url);
    }

    @JavascriptInterface
    public void log(String id, String msg) {
        store.setError(id, "");
    }

    @JavascriptInterface
    public void error(String id, String msg) {
        store.setError(id, msg);
    }

    /** new Function 被页面 CSP 拦住时的兜底入口。 */
    @JavascriptInterface
    public void runFallback(String id, String reason) {
        final BaseWebActivity a = hostRef.get();
        if (a != null) {
            ui.post(() -> a.runScriptNatively(id));
        }
    }

    // ---------------------------------------------------------------- GM 存储

    @JavascriptInterface
    public String gmGet(String id, String key) {
        return store.gmGet(id, key);
    }

    @JavascriptInterface
    public void gmSet(String id, String key, String json) {
        store.gmSet(id, key, json);
    }

    @JavascriptInterface
    public void gmDel(String id, String key) {
        store.gmDel(id, key);
    }

    @JavascriptInterface
    public String gmKeys(String id) {
        return store.gmKeys(id);
    }

    // ---------------------------------------------------------------- 原生能力

    @JavascriptInterface
    public void notify(final String title, final String text) {
        ui.post(() -> showNotification(title, text));
    }

    @JavascriptInterface
    public void openTab(final String url) {
        ui.post(() -> {
            BaseWebActivity a = hostRef.get();
            if (a != null && !a.isFinishing()) {
                a.openUrlInNewLayer(url);
                return;
            }
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                app.startActivity(i);
            } catch (Exception ignored) {
            }
        });
    }

    @JavascriptInterface
    public void menu(String id, String commandId, String caption) {
        final BaseWebActivity a = hostRef.get();
        if (a != null) {
            ui.post(() -> a.onScriptMenuCommand(id, commandId, caption));
        }
    }

    // ---------------------------------------------------------------- 通知

    private void showNotification(String title, String text) {
        NotificationManager nm =
                (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "脚本通知",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("用户脚本（如关注帖提醒）发出的通知");
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(app, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(app, 0, open, flags);

        String t = (title == null || title.isEmpty()) ? "南+" : title;
        NotificationCompat.Builder b = new NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mobile)
                .setContentTitle(t)
                .setContentText(text == null ? "" : text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text == null ? "" : text))
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            nm.notify(NOTIFY_ID, b.build());
        } catch (Exception ignored) {
        }
    }
}
