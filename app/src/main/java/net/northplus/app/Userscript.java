package net.northplus.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 用户脚本（.user.js）：元数据解析 + @match / @include 匹配。 */
public class Userscript {

    private static final Pattern HEADER = Pattern.compile(
            "(?s)//\\s*==UserScript==(.*?)//\\s*==/UserScript==");

    private static final Pattern META_LINE = Pattern.compile(
            "^\\s*//\\s*@([A-Za-z0-9:_-]+)(?:\\s+(.*?))?\\s*$");

    private static final Pattern MATCH_PATTERN = Pattern.compile(
            "^(\\*|[A-Za-z][A-Za-z0-9+.\\-]*):\\/\\/([^/]*)(\\/.*)?$");

    /** 稳定标识：同一脚本的不同版本要落到同一个 id，才能覆盖升级并保留设置。 */
    public String id = "";
    public String name = "";
    public String version = "";
    public String author = "";
    public String description = "";
    public String namespace = "";
    public String runAt = "document-end";
    public final List<String> matches = new ArrayList<>();
    public final List<String> includes = new ArrayList<>();
    public final List<String> excludes = new ArrayList<>();
    public final List<String> grants = new ArrayList<>();
    public final List<String> requires = new ArrayList<>();
    public String code = "";
    public boolean enabled = true;
    /** 最近一次执行错误，空串表示正常。 */
    public String lastError = "";

    public String displayTitle() {
        if (version == null || version.isEmpty()) return name;
        return name + "  v" + version;
    }

    public String displaySubtitle() {
        StringBuilder sb = new StringBuilder();
        if (author != null && !author.isEmpty()) sb.append("by ").append(author);
        if (description != null && !description.isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(description);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 解析

    public static Userscript parse(String source) {
        Userscript u = new Userscript();
        u.code = source == null ? "" : source;
        if (source == null) return u;

        Matcher h = HEADER.matcher(source);
        if (h.find()) {
            String[] lines = h.group(1).split("\n");
            for (String line : lines) {
                Matcher m = META_LINE.matcher(line);
                if (!m.matches()) continue;
                String key = m.group(1).toLowerCase(Locale.ROOT);
                String val = m.group(2) == null ? "" : m.group(2).trim();
                switch (key) {
                    case "name":
                        if (u.name.isEmpty()) u.name = val;
                        break;
                    case "version":
                        u.version = val;
                        break;
                    case "author":
                        u.author = val;
                        break;
                    case "description":
                        u.description = val;
                        break;
                    case "namespace":
                        u.namespace = val;
                        break;
                    case "run-at":
                        u.runAt = val.isEmpty() ? "document-end" : val;
                        break;
                    case "match":
                        if (!val.isEmpty()) u.matches.add(normalizePattern(val));
                        break;
                    case "include":
                        if (!val.isEmpty()) u.includes.add(val);
                        break;
                    case "exclude":
                        if (!val.isEmpty()) u.excludes.add(normalizePattern(val));
                        break;
                    case "grant":
                        if (!val.isEmpty()) u.grants.add(val);
                        break;
                    case "require":
                        if (!val.isEmpty()) u.requires.add(val);
                        break;
                    default:
                        break;
                }
            }
        }

        if (u.name.isEmpty()) u.name = "未命名脚本";
        u.id = makeId(u.name, u.namespace);
        return u;
    }

    /**
     * 元数据里偶尔会丢一个前导 {@code *}（如 {@code ://host/*}），补回来；
     * 同时保证路径段存在，否则匹配规则会失效。
     */
    private static String normalizePattern(String p) {
        String s = p.trim();
        if (s.startsWith("://")) s = "*" + s;
        Matcher m = MATCH_PATTERN.matcher(s);
        if (m.matches() && m.group(3) == null) s = s + "/*";
        return s;
    }

    public static String makeId(String name, String namespace) {
        String base = (namespace == null ? "" : namespace) + "/" + (name == null ? "" : name);
        String slug = name == null ? "script" : name
                .replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5]+", "_");
        if (slug.length() > 24) slug = slug.substring(0, 24);
        if (slug.isEmpty()) slug = "script";
        return "u" + Integer.toHexString(base.hashCode()) + "_" + slug;
    }

    // ------------------------------------------------------------------ 匹配

    public boolean matches(String url) {
        if (url == null || url.isEmpty()) return false;
        for (String e : excludes) {
            if (matchPattern(e, url)) return false;
        }
        for (String m : matches) {
            if (matchPattern(m, url)) return true;
        }
        for (String i : includes) {
            if (matchInclude(i, url)) return true;
        }
        return false;
    }

    /** Chrome match pattern：{@code <scheme>://<host><path>} */
    public static boolean matchPattern(String pattern, String url) {
        // 统一在这里补全，避免调用方绕过 parse() 时拿到未归一化的模式
        Matcher pm = MATCH_PATTERN.matcher(normalizePattern(pattern));
        if (!pm.matches()) return false;

        String pScheme = pm.group(1);
        String pHost = pm.group(2);
        String pPath = pm.group(3) == null ? "/*" : pm.group(3);

        android.net.Uri uri;
        try {
            uri = android.net.Uri.parse(url);
        } catch (Exception e) {
            return false;
        }
        String uScheme = uri.getScheme();
        String uHost = uri.getHost();
        if (uScheme == null || uHost == null) return false;

        if (!"*".equals(pScheme)) {
            boolean schemeOk = pScheme.equalsIgnoreCase(uScheme)
                    || ("http".equalsIgnoreCase(pScheme) && "https".equalsIgnoreCase(uScheme));
            if (!schemeOk) return false;
        }

        if (!"*".equals(pHost)) {
            String a = pHost.toLowerCase(Locale.ROOT);
            String b = uHost.toLowerCase(Locale.ROOT);
            if (a.startsWith("*.")) {
                String base = a.substring(2);
                if (!b.equals(base) && !b.endsWith("." + base)) return false;
            } else if (!a.equals(b)) {
                return false;
            }
        }

        String path = uri.getPath();
        if (path == null || path.isEmpty()) path = "/";
        String query = uri.getEncodedQuery();
        String full = query == null ? path : path + "?" + query;

        String rx = "^" + Pattern.quote(pPath).replace("*", "\\E.*\\Q") + "$";
        try {
            return Pattern.compile(rx).matcher(full).find();
        } catch (Exception e) {
            return false;
        }
    }

    /** @include 支持 match pattern、通配串与 /正则/ 三种写法。 */
    public static boolean matchInclude(String raw, String url) {
        String p = raw.trim();
        if (p.length() > 1 && p.startsWith("/") && p.endsWith("/")) {
            try {
                return Pattern.compile(p.substring(1, p.length() - 1)).matcher(url).find();
            } catch (Exception e) {
                return false;
            }
        }
        if (MATCH_PATTERN.matcher(normalizePattern(p)).matches()) {
            return matchPattern(normalizePattern(p), url);
        }
        String rx = "^" + Pattern.quote(p).replace("*", "\\E.*\\Q") + "$";
        try {
            return Pattern.compile(rx).matcher(url).find();
        } catch (Exception e) {
            return false;
        }
    }
}
