package net.northplus.app;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {

    private static App sInstance;
    public static Prefs prefs;

    /** 轻量后台任务的公共线程池。 */
    public static final ExecutorService EXEC = Executors.newCachedThreadPool();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        prefs = new Prefs(this);
        Prefs.applyNightMode(prefs.night());
        // 内置脚本在首次启动时落地：先装 assets 里随包附带的，
        // 再检查凛+ 是否已存在，没有就静默下载一次。之后就是本地脚本，可正常开关与更新。
        EXEC.execute(() -> {
            UserscriptStore store = UserscriptStore.get(this);
            store.installBundled();
            if (store.findRin() == null) {
                UserscriptInstaller.installSilently(this,
                        UserscriptSources.RIN_ID, UserscriptSources.RIN_NAME, null);
            }
        });

        String savedHost = prefs.siteHost();
        if (savedHost != null && !savedHost.isEmpty()) Site.setHost(savedHost);
    }

    public static Context ctx() {
        return sInstance.getApplicationContext();
    }

    public static void runOnUi(Runnable r) {
        UI.post(r);
    }
}
