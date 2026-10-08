package net.northplus.app;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 「下载 → 解析 → 确认 → 安装」的统一入口，供网页里的 .user.js 链接与管理界面共用。
 *
 * <p>下载走**多源并行竞速**：greasyfork.org 在国内连不上，必须以内置镜像为主。
 * 按顺序死等会在镜像失效时叠加超时、把安装卡死，所以这里同时发起若干请求，
 * 谁先返回合法脚本就用谁，并把成功的源记下来供下次优先使用。
 */
public final class UserscriptInstaller {

    public interface Callback {
        void onResult(boolean installed, String message);
    }

    public interface ProbeCallback {
        void onResult(boolean[] reachable);
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    /** 竞速用短超时，避免一个死源拖住整体。 */
    private static final int CONNECT_MS = 6000;
    private static final int READ_MS = 12000;
    private static final int RACE_TIMEOUT_MS = 22000;

    private static final String MARKER = "==UserScript==";

    private static final class Found {
        final String body;
        final String sourceLabel;
        final String url;

        Found(String body, String sourceLabel, String url) {
            this.body = body;
            this.sourceLabel = sourceLabel;
            this.url = url;
        }
    }

    private UserscriptInstaller() {
    }

    // ------------------------------------------------------------------ 入口

    /** 从任意地址安装：发布页地址、.user.js 直链、自定义地址都能吃。 */
    public static void installFromUrl(Activity act, String url, Callback cb) {
        installFromReference(act, url, null, cb);
    }

    /** 已知脚本 ID（推荐脚本一键安装）走这里，能直接拼出各源的规范直链。 */
    public static void installById(Activity act, String scriptId, String scriptName, Callback cb) {
        installFromReference(act,
                "https://greasyfork.org/zh-CN/scripts/" + scriptId, scriptName, cb);
    }

    /**
     * 静默安装（无任何对话框），用于首次启动让内置脚本落地。
     * 失败不打扰用户——脚本页里还有「一键更新凛+」可以重试。
     */
    public static void installSilently(final Context ctx, final String scriptId,
                                       final String scriptName, final Callback cb) {
        POOL.execute(() -> {
            List<String[]> candidates = buildCandidates(
                    "https://greasyfork.org/zh-CN/scripts/" + scriptId, scriptName);
            Found found = race(candidates, MARKER);
            if (found == null) found = findByPage(scriptId);
            final Found f = found;
            UI.post(() -> {
                if (f == null) {
                    if (cb != null) cb.onResult(false, "所有下载源都不可用");
                    return;
                }
                App.prefs.setUserscriptSource(f.sourceLabel);
                Userscript u = UserscriptStore.get(ctx).install(f.body);
                if (cb != null) {
                    cb.onResult(u != null, u == null ? "写入失败" : u.displayTitle());
                }
            });
        });
    }

    /** 本地文件内容安装。 */
    public static void installFromSource(Activity act, String source, Callback cb) {
        if (act == null || source == null) return;
        if (!source.contains(MARKER)) {
            fail(act, cb, "不是有效的用户脚本（缺少 " + MARKER + " 头）");
            return;
        }
        confirmAndInstall(act, source, cb, null);
    }

    // ------------------------------------------------------------------ 下载

    private static void installFromReference(final Activity act, final String reference,
                                             final String scriptName, final Callback cb) {
        if (act == null || TextUtils.isEmpty(reference)) return;
        toast(act, "正在从多个源下载脚本…");

        POOL.execute(() -> {
            List<String[]> candidates = buildCandidates(reference, scriptName);
            Found found = race(candidates, MARKER);

            final String id = UserscriptSources.scriptId(reference);
            if (found == null && id != null) {
                // 兜底：抓发布页 HTML，用页面自己声明的安装链接（应对镜像路径结构不同）
                found = findByPage(id);
            }

            final Found f = found;
            UI.post(() -> {
                if (act.isFinishing()) return;
                if (f == null) {
                    fail(act, cb, "所有下载源都没拿到脚本。可在「下载源」里换一个源，"
                            + "或点「打开发布页」手动安装。");
                    return;
                }
                App.prefs.setUserscriptSource(f.sourceLabel);
                confirmAndInstall(act, f.body, cb, f.sourceLabel);
            });
        });
    }

    private static List<String[]> buildCandidates(String reference, String scriptName) {
        List<String[]> raw = new ArrayList<>();
        String id = UserscriptSources.scriptId(reference);
        boolean direct = reference.toLowerCase().contains(".user.js");

        if (direct) {
            raw.addAll(UserscriptSources.directCandidates(reference));
        }
        if (id != null) {
            raw.addAll(UserscriptSources.installCandidates(id, scriptName));
        }
        if (raw.isEmpty()) {
            raw.add(new String[]{reference, "自定义地址"});
        }

        // 去重：非 greasyfork 地址在各源下会被改写成同一个 URL，没必要重复请求
        Set<String> seen = new HashSet<>();
        List<String[]> out = new ArrayList<>();
        for (String[] c : raw) {
            if (seen.add(c[0])) out.add(c);
        }
        return orderByPreferred(out);
    }

    /** 把上次成功的源排到前面，减少无用请求。 */
    private static List<String[]> orderByPreferred(List<String[]> in) {
        String preferred = App.prefs.userscriptSource();
        if (preferred == null || preferred.isEmpty()) return in;
        List<String[]> first = new ArrayList<>();
        List<String[]> rest = new ArrayList<>();
        for (String[] c : in) {
            if (preferred.equals(c[1])) first.add(c);
            else rest.add(c);
        }
        if (first.isEmpty()) return in;
        first.addAll(rest);
        return first;
    }

    /** 并发发起全部候选，取第一个拿到合法脚本的结果。 */
    private static Found race(List<String[]> candidates, String mustContain) {
        if (candidates == null || candidates.isEmpty()) return null;

        final AtomicReference<Found> hit = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, candidates.size()));
        for (final String[] c : candidates) {
            pool.execute(() -> {
                if (hit.get() != null) return;
                String body = Net.getText(c[0], Site.base() + "/", CONNECT_MS, READ_MS);
                if (body != null && !body.isEmpty()
                        && (mustContain == null || body.contains(mustContain))) {
                    hit.compareAndSet(null, new Found(body, c[1], c[0]));
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(RACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();
        return hit.get();
    }

    /** 各镜像的发布页并行抓取，用页面自己声明的 .user.js 链接安装。 */
    private static Found findByPage(String scriptId) {
        int id;
        try {
            id = Integer.parseInt(scriptId);
        } catch (Exception e) {
            return null;
        }
        final List<String[]> pages = UserscriptSources.pageCandidates(id);
        final AtomicReference<Found> hit = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, pages.size()));
        for (final String[] p : pages) {
            pool.execute(() -> {
                if (hit.get() != null) return;
                String html = Net.getText(p[0], Site.base() + "/", CONNECT_MS, READ_MS);
                if (html == null) return;
                String link = UserscriptSources.discoverInstallLink(p[0], html, true);
                if (link == null) return;
                String body = Net.getText(link, p[0], CONNECT_MS, READ_MS);
                if (body != null && body.contains(MARKER)) {
                    hit.compareAndSet(null, new Found(body, p[1], link));
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(RACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        pool.shutdownNow();
        return hit.get();
    }

    // ------------------------------------------------------------------ 源探测

    /** 并行探测各下载源是否可达，用于「下载源」界面。 */
    public static void probeSources(final ProbeCallback cb) {
        POOL.execute(() -> {
            final boolean[] ok = new boolean[UserscriptSources.SOURCES.length];
            final CountDownLatch latch = new CountDownLatch(ok.length);
            ExecutorService pool = Executors.newFixedThreadPool(ok.length);
            for (int i = 0; i < ok.length; i++) {
                final int idx = i;
                pool.execute(() -> {
                    try {
                        String url = "https://" + UserscriptSources.SOURCES[idx].pageHost + "/";
                        String html = Net.getText(url, Site.base() + "/", CONNECT_MS, READ_MS);
                        ok[idx] = html != null && html.length() > 64;
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
            UI.post(() -> cb.onResult(ok));
        });
    }

    // ------------------------------------------------------------------ 确认与安装

    private static void confirmAndInstall(Activity act, String source, Callback cb,
                                          String sourceLabel) {
        final Userscript parsed = Userscript.parse(source);
        boolean existing = UserscriptStore.get(act).byId(parsed.id) != null;

        StringBuilder sb = new StringBuilder();
        sb.append(parsed.name);
        if (!parsed.version.isEmpty()) sb.append("  v").append(parsed.version);
        if (!parsed.author.isEmpty()) sb.append("\n作者：").append(parsed.author);
        int rules = parsed.matches.size() + parsed.includes.size();
        sb.append("\n生效范围：")
                .append(rules == 0 ? "未声明（安装了也不会运行）" : rules + " 条规则");
        sb.append("\n运行时机：").append(parsed.runAt);
        if (sourceLabel != null) sb.append("\n下载源：").append(sourceLabel);
        if (!parsed.description.isEmpty()) sb.append("\n\n").append(parsed.description);
        sb.append("\n\n脚本将在本应用的网页中以网页权限运行，可读写页面内容与登录状态。");

        new AlertDialog.Builder(act)
                .setTitle(existing ? "更新用户脚本？" : "安装用户脚本？")
                .setMessage(sb.toString())
                .setNegativeButton("取消", (d, w) -> {
                    if (cb != null) cb.onResult(false, "已取消");
                })
                .setPositiveButton(existing ? "更新" : "安装", (d, w) -> {
                    Userscript u = UserscriptStore.get(act).install(source);
                    if (u == null) {
                        fail(act, cb, "安装失败（脚本内容无法写入）");
                        return;
                    }
                    String msg = (existing ? "已更新：" : "已安装：") + u.displayTitle();
                    toast(act, msg);
                    if (cb != null) cb.onResult(true, msg);
                })
                .show();
    }

    private static void fail(Activity act, Callback cb, String msg) {
        toast(act, msg);
        if (cb != null) cb.onResult(false, msg);
    }

    private static void toast(Activity a, String m) {
        Toast.makeText(a, m, Toast.LENGTH_LONG).show();
    }

    /** 当前优先使用的下载源。 */
    public static UserscriptSources.Source currentSource() {
        String label = App.prefs.userscriptSource();
        return label == null || label.isEmpty()
                ? UserscriptSources.SOURCES[0]
                : UserscriptSources.byLabel(label);
    }
}
