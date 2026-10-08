package net.northplus.app;

import android.net.Uri;

/**
 * 访问站点（域名）的运行时配置 —— 纯逻辑，不依赖 Android 框架，便于离线单测。
 *
 * <p>这一族论坛有多个域名指向同一套论坛程序。哪个能连上因网络环境而异，
 * 所以主机名不写死：内置候选列表 + 运行时切换 + 允许手动指定。
 *
 * <p>持久化由调用方负责（{@link Prefs#setSiteHost}）；
 * 网络探测见 {@link SiteProbe}。
 *
 * <p>注意：Cookie 按域名保存，换域名后需要重新登录。
 */
public final class Site {

    public static final class Entry {
        public final String host;
        public final String label;

        public Entry(String host, String label) {
            this.host = host;
            this.label = label;
        }

        public String base() {
            return "https://" + host;
        }

        @Override
        public String toString() {
            return label + "　" + host;
        }
    }

    /** 内置候选；首个为默认。域名与叫法取自站点清单。 */
    public static final Entry[] BUILT_IN = {
            new Entry("north-plus.net", "北+"),
            new Entry("soul-plus.net", "魂+"),
            new Entry("south-plus.net", "南+"),
            new Entry("white-plus.net", "白+"),
            new Entry("level-plus.net", "Lv+"),
            new Entry("summer-plus.net", "夏+"),
            new Entry("spring-plus.net", "春+"),
            new Entry("snow-plus.net", "雪+"),
            new Entry("east-plus.net", "东+"),
            new Entry("blue-plus.net", "蓝+"),
    };

    private static volatile String sHost = BUILT_IN[0].host;

    private Site() {
    }

    public static String host() {
        return sHost;
    }

    public static String base() {
        return "https://" + sHost;
    }

    public static String home() {
        return base() + "/index.php";
    }

    public static String homeMobile() {
        return base() + "/simple/";
    }

    public static String me() {
        return base() + "/u.php";
    }

    public static String login() {
        return base() + "/login.php";
    }

    public static String search() {
        return base() + "/search.php";
    }

    /** 带 Cookie 的请求用它当 Referer。 */
    public static String referer() {
        return base() + "/";
    }

    public static boolean isCurrent(String host) {
        return host != null && host.equalsIgnoreCase(sHost);
    }

    /** 规范化并切换生效域名（不落盘）。接受带协议、路径、端口的写法。 */
    public static boolean setHost(String raw) {
        String h = normalize(raw);
        if (h == null) return false;
        sHost = h;
        return true;
    }

    static String normalize(String raw) {
        if (raw == null) return null;
        String h = raw.trim().toLowerCase();
        int scheme = h.indexOf("://");
        if (scheme >= 0) h = h.substring(scheme + 3);
        int slash = h.indexOf('/');
        if (slash >= 0) h = h.substring(0, slash);
        int q = h.indexOf('?');
        if (q >= 0) h = h.substring(0, q);
        if (h.isEmpty() || !h.contains(".")) return null;
        // 只保留主机名字符
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == ':';
            if (!ok) return null;
        }
        return h;
    }

    public static boolean isBuiltIn(String host) {
        if (host == null) return false;
        String h = stripWww(host);
        for (Entry e : BUILT_IN) {
            if (stripWww(e.host).equalsIgnoreCase(h)) return true;
        }
        return false;
    }

    public static Entry entryOf(String host) {
        if (host == null) return null;
        for (Entry e : BUILT_IN) {
            if (e.host.equalsIgnoreCase(host)) return e;
        }
        return null;
    }

    /** 展示用名称：内置站点给中文名，自定义域名回退成主机名。 */
    public static String displayName(String host) {
        Entry e = entryOf(host);
        return e != null ? e.label : host;
    }

    public static String hostOf(String url) {
        try {
            return Uri.parse(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripWww(String h) {
        return h.startsWith("www.") ? h.substring(4) : h;
    }
}
