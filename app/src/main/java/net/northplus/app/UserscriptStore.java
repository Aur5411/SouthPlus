package net.northplus.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户脚本仓库：安装 / 启停 / 删除 / 匹配查询 / GM 键值存储。
 *
 * <p>脚本放在 {@code filesDir/userscripts/*.user.js}；启用状态与 GM 存储走
 * SharedPreferences，保证重启后脚本的配置还在（与油猴行为一致）。
 * 所有对外方法都做了同步——JS 桥是在 WebView 的 JS 线程上调进来的。
 */
public class UserscriptStore {

    private static final String SFILE_SUFFIX = ".user.js";
    private static final String SP_ENABLED = "np_us_enabled";
    private static final String SP_GM = "np_us_gm";
    private static final char GM_SEP = '\u0001';

    private static volatile UserscriptStore sInstance;

    private final File dir;
    private final SharedPreferences enabledPref;
    private final SharedPreferences gmPref;
    private final List<Userscript> cache = new ArrayList<>();
    private final Map<String, String> errors = new HashMap<>();

    private UserscriptStore(Context ctx) {
        Context c = ctx.getApplicationContext();
        dir = new File(c.getFilesDir(), "userscripts");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        enabledPref = c.getSharedPreferences(SP_ENABLED, Context.MODE_PRIVATE);
        gmPref = c.getSharedPreferences(SP_GM, Context.MODE_PRIVATE);
        reload();
    }

    public static UserscriptStore get(Context ctx) {
        if (sInstance == null) {
            synchronized (UserscriptStore.class) {
                if (sInstance == null) sInstance = new UserscriptStore(ctx);
            }
        }
        return sInstance;
    }

    // ------------------------------------------------------------------ 载入

    public synchronized void reload() {
        cache.clear();
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isFile() || !f.getName().endsWith(SFILE_SUFFIX)) continue;
            String src = readFile(f);
            if (src == null) continue;
            Userscript u = Userscript.parse(src);
            u.enabled = enabledPref.getBoolean(u.id, true);
            u.lastError = errors.get(u.id);
            cache.add(u);
        }
        Collections.sort(cache, (a, b) -> a.name.compareToIgnoreCase(b.name));
    }

    private static String readFile(File f) {
        try {
            byte[] b = Files.readAllBytes(f.toPath());
            return new String(b, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized List<Userscript> all() {
        return new ArrayList<>(cache);
    }

    public synchronized Userscript byId(String id) {
        for (Userscript u : cache) {
            if (u.id.equals(id)) return u;
        }
        return null;
    }

    /** 找已安装的凛+：先按稳定 id，再按名字兜底。 */
    public synchronized Userscript findRin() {
        Userscript u = byId(Userscript.makeId(
                UserscriptSources.RIN_NAME, UserscriptSources.RIN_NAMESPACE));
        if (u != null) return u;
        for (Userscript s : cache) {
            if (s.name != null && s.name.contains("凛")) return s;
        }
        return null;
    }

    public synchronized int enabledCount() {
        int n = 0;
        for (Userscript u : cache) if (u.enabled) n++;
        return n;
    }

    // ------------------------------------------------------------------ 安装

    /**
     * 安装或升级。同一 {@code @name}+{@code @namespace} 会落到同一个 id，
     * 因此升级不会丢掉启用状态与 GM 配置。
     *
     * @return 解析出的脚本对象；失败返回 null
     */
    public synchronized Userscript install(String source) {
        if (source == null || source.trim().isEmpty()) return null;
        if (source.indexOf("==UserScript==") < 0 && source.indexOf("function") < 0) return null;

        Userscript parsed = Userscript.parse(source);
        try {
            File f = new File(dir, parsed.id + SFILE_SUFFIX);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(source.getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
        } catch (IOException e) {
            return null;
        }
        if (!enabledPref.contains(parsed.id)) {
            enabledPref.edit().putBoolean(parsed.id, true).commit();
        }
        errors.remove(parsed.id);
        reload();
        return byId(parsed.id);
    }

    /**
     * 安装随 APK 内置的脚本（assets/userscripts/*.user.js）。
     * 已经装过同 id 的脚本就跳过，避免覆盖用户的更新版本。
     */
    public synchronized int installBundled() {
        int n = 0;
        try {
            String[] files = App.ctx().getAssets().list("userscripts");
            if (files == null) return 0;
            for (String f : files) {
                if (!f.endsWith(".user.js")) continue;
                String src = readAsset("userscripts/" + f);
                if (src == null || !src.contains("==UserScript==")) continue;
                Userscript parsed = Userscript.parse(src);
                if (byId(parsed.id) != null) continue;
                if (install(src) != null) n++;
            }
        } catch (Exception ignored) {
        }
        return n;
    }

    private static String readAsset(String path) {
        try (java.io.InputStream in = App.ctx().getAssets().open(path)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从网络安装，返回结果描述（用于 toast）。 */
    public synchronized String installFromUrl(String url) {
        String body = Net.getText(url, Site.base() + "/");
        if (body == null || body.trim().isEmpty()) {
            return "下载失败，请检查网络或链接";
        }
        Userscript u = install(body);
        if (u == null) return "不是有效的用户脚本";
        return "已安装：" + u.displayTitle();
    }

    public synchronized void setEnabled(String id, boolean on) {
        enabledPref.edit().putBoolean(id, on).commit();
        Userscript u = byId(id);
        if (u != null) u.enabled = on;
    }

    public synchronized void remove(String id) {
        File f = new File(dir, id + SFILE_SUFFIX);
        if (f.exists()) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        enabledPref.edit().remove(id).commit();
        clearGm(id);
        errors.remove(id);
        reload();
    }

    // ------------------------------------------------------------------ GM 存储

    private static String gmKey(String id, String key) {
        return id + GM_SEP + key;
    }

    public synchronized String gmGet(String id, String key) {
        return gmPref.getString(gmKey(id, key), "");
    }

    public synchronized void gmSet(String id, String key, String json) {
        gmPref.edit().putString(gmKey(id, key), json).commit();
    }

    public synchronized void gmDel(String id, String key) {
        gmPref.edit().remove(gmKey(id, key)).commit();
    }

    public synchronized String gmKeys(String id) {
        JSONArray arr = new JSONArray();
        String prefix = id + GM_SEP;
        for (String k : gmPref.getAll().keySet()) {
            if (k.startsWith(prefix)) arr.put(k.substring(prefix.length()));
        }
        return arr.toString();
    }

    private void clearGm(String id) {
        SharedPreferences.Editor ed = gmPref.edit();
        String prefix = id + GM_SEP;
        for (String k : gmPref.getAll().keySet()) {
            if (k.startsWith(prefix)) ed.remove(k);
        }
        ed.commit();
    }

    // ------------------------------------------------------------------ 运行期查询

    /** 记录脚本执行错误，供管理界面展示。 */
    public synchronized void setError(String id, String message) {
        if (message == null || message.isEmpty()) {
            errors.remove(id);
        } else {
            errors.put(id, message.length() > 400 ? message.substring(0, 400) : message);
        }
        Userscript u = byId(id);
        if (u != null) u.lastError = errors.containsKey(id) ? errors.get(id) : "";
    }

    /**
     * 当前 URL 应当运行哪些脚本，返回带源码的 JSON 数组。
     * 由 {@code host.js} 在 document-start 同步调用，因此这里只做内存匹配。
     */
    public synchronized String jsonFor(String url) {
        JSONArray arr = new JSONArray();
        for (Userscript u : cache) {
            if (!u.enabled) continue;
            if (!u.matches(url)) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("id", u.id);
                o.put("name", u.name);
                o.put("version", u.version);
                o.put("description", u.description);
                o.put("runAt", u.runAt);
                o.put("code", u.code);
                o.put("matches", new JSONArray(u.matches));
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr.toString();
    }

    /** 取脚本源码本体，供原生兜底注入使用。 */
    public synchronized String codeOf(String id) {
        Userscript u = byId(id);
        return u == null ? null : u.code;
    }

    public static String fileNameOf(Userscript u) {
        return u.id + SFILE_SUFFIX;
    }
}
