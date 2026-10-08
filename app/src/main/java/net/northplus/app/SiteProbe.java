package net.northplus.app;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 站点连通性探测（需要网络，与 {@link Site} 的纯配置部分分开）。 */
public final class SiteProbe {

    public interface ProbeCallback {
        /** 与 {@link Site#BUILT_IN} 一一对应的可达性。 */
        void onResult(boolean[] reachable);
    }

    public interface PickedCallback {
        /** 选中的候选下标；全部不可达为 -1。 */
        void onResult(int index, String host);
    }

    private static final int CONNECT_MS = 6000;
    private static final int READ_MS = 9000;

    private SiteProbe() {
    }

    /** 并行探测所有内置站点。 */
    public static void probe(final ProbeCallback cb) {
        App.EXEC.execute(() -> {
            final Site.Entry[] all = Site.BUILT_IN;
            final boolean[] ok = new boolean[all.length];
            final CountDownLatch latch = new CountDownLatch(ok.length);
            ExecutorService pool = Executors.newFixedThreadPool(ok.length);
            for (int i = 0; i < all.length; i++) {
                final int idx = i;
                pool.execute(() -> {
                    try {
                        ok[idx] = Net.ping(all[idx].base() + "/", CONNECT_MS, READ_MS);
                    } catch (Throwable ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                latch.await(CONNECT_MS + READ_MS + 4000L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            pool.shutdownNow();
            App.runOnUi(() -> cb.onResult(ok));
        });
    }

    /**
     * 挑一个能直连的站点：当前站点可达就保留，否则按候选顺序取第一个可达的。
     */
    public static void pickReachable(final PickedCallback cb) {
        probe(reachable -> {
            final Site.Entry[] all = Site.BUILT_IN;
            for (int i = 0; i < all.length; i++) {
                if (all[i].host.equalsIgnoreCase(Site.host()) && reachable[i]) {
                    cb.onResult(i, all[i].host);
                    return;
                }
            }
            for (int i = 0; i < reachable.length; i++) {
                if (reachable[i]) {
                    cb.onResult(i, all[i].host);
                    return;
                }
            }
            cb.onResult(-1, null);
        });
    }
}
