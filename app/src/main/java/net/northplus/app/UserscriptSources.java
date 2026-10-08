package net.northplus.app;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户脚本下载源。
 *
 * <p>greasyfork.org 自 2025-02-06 起在国内无法直连，所以内置一份镜像源清单，
 * **国内镜像在前、官方源兜底**。镜像与官方的路径结构一致，只换主机名，
 * 因此这里用「主机替换」生成候选地址，而不是写死一堆完整 URL。
 *
 * <p>镜像站随时可能失效，所以调用方要用「并行竞速 + 取首个成功 + 记住可用源」的方式，
 * 不要按顺序死等——顺序超时叠加会让安装卡到没法用。
 */
public final class UserscriptSources {

    public static final class Source {
        public final String pageHost;
        public final String updateHost;
        public final String label;
        public final boolean domestic;

        public Source(String pageHost, String updateHost, String label, boolean domestic) {
            this.pageHost = pageHost;
            this.updateHost = updateHost;
            this.label = label;
            this.domestic = domestic;
        }

        public String pageUrl(String path) {
            return "https://" + pageHost + (path == null ? "/" : path);
        }

        @Override
        public String toString() {
            return label + "（" + pageHost + "）";
        }
    }

    /** 顺序即优先级：国内镜像在前，官方源兜底。 */
    public static final Source[] SOURCES = {
            new Source("gf.qytechs.cn", "update.gf.qytechs.cn", "奇趣镜像", true),
            new Source("greasyfork.cc", "update.greasyfork.cc", "greasyfork.cc", true),
            new Source("greasyfork.cloud", "update.greasyfork.cloud", "greasyfork.cloud", true),
            new Source("greasyfork-zh.org", "update.greasyfork-zh.org", "greasyfork-zh", true),
            new Source("greasyfork.org", "update.greasyfork.org", "官方源", false),
    };

    /** 凛+ 的脚本标识（内置脚本用）。 */
    public static final String RIN_ID = "454120";
    public static final String RIN_NAME = "南加北加论坛强化脚本(凛+)";
    public static final String RIN_NAMESPACE = "tousakarin";

    /** 官方源在清单里的下标。 */
    public static final int OFFICIAL_INDEX = SOURCES.length - 1;

    private static final Pattern P_SCRIPT_ID = Pattern.compile("/scripts/(\\d+)");
    private static final Pattern P_USERJS_HREF =
            Pattern.compile("href=\"([^\"]*?\\.user\\.js)\"", Pattern.CASE_INSENSITIVE);

    private UserscriptSources() {
    }

    public static Source byPageHost(String host) {
        if (host != null) {
            for (Source s : SOURCES) {
                if (s.pageHost.equalsIgnoreCase(host)) return s;
            }
        }
        return SOURCES[0];
    }

    public static Source byLabel(String label) {
        if (label != null) {
            for (Source s : SOURCES) {
                if (s.label.equals(label)) return s;
            }
        }
        return SOURCES[0];
    }

    /** 任意 greasyfork 系地址（发布页或直链）里的数字脚本 ID。 */
    public static String scriptId(String url) {
        if (url == null) return null;
        Matcher m = P_SCRIPT_ID.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** 地址是否属于已知的脚本源主机。 */
    public static boolean isSourceHost(String url) {
        if (url == null) return false;
        for (Source s : SOURCES) {
            if (url.contains("://" + s.pageHost) || url.contains("://" + s.updateHost)) {
                return true;
            }
        }
        return false;
    }

    /** 把 greasyfork 系主机换到指定源（发布页与直链都适用）。 */
    public static String rewrite(String url, Source target) {
        if (url == null || target == null) return url;
        String out = url;
        for (Source s : SOURCES) {
            out = replaceHost(out, s.pageHost, target.pageHost);
            out = replaceHost(out, s.updateHost, target.updateHost);
        }
        return out;
    }

    private static String replaceHost(String url, String from, String to) {
        if (from.equals(to)) return url;
        String needle = "://" + from;
        int i = url.indexOf(needle);
        if (i < 0) return url;
        int afterHost = i + needle.length();
        // 必须是主机结尾（后一个字符是 / ? # : 或字符串结束），避免匹配到 evil-from.com
        if (afterHost < url.length()) {
            char c = url.charAt(afterHost);
            if (c != '/' && c != '?' && c != '#' && c != ':') return url;
        }
        int slash = indexOfAny(url, afterHost, "/?#");
        String tail = slash >= 0 ? url.substring(slash) : "";
        return url.substring(0, i) + "://" + to + tail;
    }

    private static int indexOfAny(String s, int from, String chars) {
        for (int i = from; i < s.length(); i++) {
            if (chars.indexOf(s.charAt(i)) >= 0) return i;
        }
        return -1;
    }

    /**
     * 直链安装候选：每个源先试「带脚本名的规范直链」，再试「只带 ID 的简写」。
     * 返回元素为 {@code [url, sourceLabel]}。
     */
    public static List<String[]> installCandidates(String scriptId, String scriptName) {
        List<String[]> out = new ArrayList<>();
        if (scriptId == null || scriptId.isEmpty()) return out;
        String enc = encodeName(scriptName);
        for (Source s : SOURCES) {
            if (enc != null) {
                out.add(new String[]{
                        "https://" + s.updateHost + "/scripts/" + scriptId + "/" + enc + ".user.js",
                        s.label});
            }
            out.add(new String[]{
                    "https://" + s.updateHost + "/scripts/" + scriptId + ".user.js",
                    s.label});
        }
        return out;
    }

    /** 把给定的 .user.js 直链扩展到各镜像。 */
    public static List<String[]> directCandidates(String url) {
        List<String[]> out = new ArrayList<>();
        if (url == null) return out;
        for (Source s : SOURCES) {
            out.add(new String[]{rewrite(url, s), s.label});
        }
        return out;
    }

    /** 发布页的候选地址（各镜像）。 */
    public static List<String[]> pageCandidates(int scriptId) {
        List<String[]> out = new ArrayList<>();
        String path = "/zh-CN/scripts/" + scriptId;
        for (Source s : SOURCES) {
            out.add(new String[]{s.pageUrl(path), s.label});
        }
        return out;
    }

    /** 从发布页 HTML 里抠出它自己声明的 .user.js 地址（镜像路径结构不一致时的兜底）。 */
    public static String discoverInstallLink(String pageUrl, String html, boolean preferDomestic) {
        if (html == null) return null;
        Matcher m = P_USERJS_HREF.matcher(html);
        while (m.find()) {
            String href = m.group(1);
            if (href == null) continue;
            if (href.startsWith("//")) href = "https:" + href;
            else if (href.startsWith("/")) {
                int schemeEnd = pageUrl.indexOf("://");
                int slash = schemeEnd >= 0 ? pageUrl.indexOf('/', schemeEnd + 3) : -1;
                String root = slash >= 0 ? pageUrl.substring(0, slash) : pageUrl;
                href = root + href;
            } else if (!href.startsWith("http")) {
                continue;
            }
            if (preferDomestic) {
                // 页面自己可能给的是官方域名，换回当前镜像
                href = rewrite(href, byPageHost(hostOf(pageUrl)));
            }
            return href;
        }
        return null;
    }

    private static String hostOf(String url) {
        try {
            return android.net.Uri.parse(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static String encodeName(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        try {
            String e = URLEncoder.encode(name.trim(), "UTF-8");
            // URLEncoder 把空格编成 '+'；真正的 '+' 已经被编成 %2B，所以这里可以安全替换
            return e.replace("+", "%20");
        } catch (Exception ex) {
            return null;
        }
    }
}
