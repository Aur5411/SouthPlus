package net.northplus.app;

import android.net.Uri;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 站点地址映射与页面类型判定。
 *
 * <p>实测 URL 规则：
 * <ul>
 *   <li>桌面版：首页 {@code /index.php}；分类 {@code /index.php?cateid-N.html}；
 *       版块 {@code /thread.php?fid-N[-page-M].html}；帖子 {@code /read.php?tid-N[&amp;page=M]}</li>
 *   <li>移动版（PHPWind 移动模板）：版块 {@code /simple/index.php?fN[_page].html}；
 *       帖子 {@code /simple/index.php?tN[_page].html}</li>
 * </ul>
 *
 * <p>站点有一整套镜像域（south-plus / blue-plus / level-plus …）。桌面版页面里会出现指向
 * 镜像域的**绝对**链接，直接跟过去会丢掉当前域的登录 Cookie。因此这里统一把镜像域
 * 改写成主站域（{@link #standardize}），保证会话一致、且全部留在应用内打开。
 */
public final class UrlMapper {

    /** 镜像域：&lt;前缀&gt;-plus.net/org，允许 www. 前缀。 */
    public static final String MIRROR_HOSTS =
            "(?i)^(?:www\\.)?(?:east|south|north|level|soul|white|blue|snow|spring|summer)"
                    + "-plus\\.(?:net|org)$";

    private static final Pattern MIRROR = Pattern.compile(MIRROR_HOSTS);

    private static final Pattern P_TID_Q = Pattern.compile("[?&]tid[=\\-](\\d+)");
    private static final Pattern P_FID_Q = Pattern.compile("[?&]fid[=\\-](\\d+)");
    private static final Pattern P_SIMPLE_T = Pattern.compile("[?&]t(\\d+)(?:_(\\d+))?\\.html");
    private static final Pattern P_SIMPLE_F = Pattern.compile("[?&][fp](\\d+)(?:_(\\d+))?\\.html");
    private static final Pattern P_PAGE = Pattern.compile("[?&]page[=\\-](\\d+)");
    private static final Pattern P_PAGE_DASH = Pattern.compile("-page-(\\d+)");
    private static final Pattern P_DASH_PAGE = Pattern.compile("/thread-\\d+-(\\d+)-");

    private UrlMapper() {
    }

    // ------------------------------------------------------------------ 主机

    /** 本站（含镜像域，以及运行时切换到的任意域名）。 */
    public static boolean isSite(String url) {
        if (url == null) return false;
        try {
            String h = Uri.parse(url).getHost();
            if (h == null) return false;
            if (Site.isCurrent(h)) return true;
            return MIRROR.matcher(h).matches() || Site.isBuiltIn(h);
        } catch (Exception e) {
            return false;
        }
    }

    /** 不是当前域、但属于同族的主机 —— 这些地址要改写到当前域。 */
    public static boolean isMirrorHost(String host) {
        if (host == null) return false;
        if (Site.isCurrent(host)) return false;
        return MIRROR.matcher(host).matches() || Site.isBuiltIn(host);
    }

    /** 把 {@code //host/x}、{@code /x} 之类补成绝对地址。 */
    public static String absolutize(String url) {
        if (url == null) return null;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return Site.base() + url;
        return url;
    }

    /** 绝对化 + 镜像域改写为主站域。站外地址原样返回。 */
    public static String standardize(String url) {
        String u = absolutize(url);
        if (u == null) return null;
        try {
            Uri uri = Uri.parse(u);
            String h = uri.getHost();
            if (isMirrorHost(h)) {
                int schemeEnd = u.indexOf("://");
                String rest = schemeEnd >= 0 ? u.substring(schemeEnd + 3) : u;
                int slash = rest.indexOf('/');
                String tail = slash >= 0 ? rest.substring(slash) : "";
                return Site.base() + tail;
            }
        } catch (Exception ignored) {
        }
        return u;
    }

    // ------------------------------------------------------------------ 地址转换

    private static boolean isSimple(String url) {
        return url != null && url.toLowerCase().contains("/simple/");
    }

    /** 桌面版地址 → 移动版地址；无法识别时原样返回。 */
    public static String toMobile(String raw) {
        String url = standardize(raw);
        if (url == null || !isSite(url)) return url;
        String low = url.toLowerCase();
        if (isSimple(low)) return url;

        long tid = firstLong(P_TID_Q, url);
        if (tid > 0) {
            return Site.base() + "/simple/index.php?t" + tid + pageSuffix(pageOf(url)) + ".html";
        }

        long fid = firstLong(P_FID_Q, url);
        if (fid > 0 && (low.contains("thread.php") || low.contains("index.php"))) {
            return Site.base() + "/simple/index.php?f" + fid + pageSuffix(pageOf(url)) + ".html";
        }

        String path;
        try {
            path = Uri.parse(url).getPath();
        } catch (Exception e) {
            path = null;
        }
        boolean rootPath = path == null || path.isEmpty() || "/".equals(path)
                || "/index.php".equals(path);
        if (rootPath && !low.contains("cateid")) return Site.homeMobile();

        return url;
    }

    /** 移动版地址 → 桌面版地址；无法识别时原样返回。 */
    public static String toDesktop(String raw) {
        String url = standardize(raw);
        if (url == null || !isSite(url)) return url;
        if (!isSimple(url)) return url;

        long tid = firstLong(P_SIMPLE_T, url);
        if (tid > 0) {
            int page = pageOf(url);
            return Site.base() + "/read.php?tid=" + tid + (page > 1 ? "&page=" + page : "");
        }

        long fid = firstLong(P_SIMPLE_F, url);
        if (fid > 0) {
            int page = pageOf(url);
            return Site.base() + "/thread.php?fid-" + fid
                    + (page > 1 ? "-page-" + page : "") + ".html";
        }

        return Site.home();
    }

    // ------------------------------------------------------------------ 类型判定

    /** 帖子页。两套形态都认，否则站点自身的重定向会让层级判定失效。 */
    public static boolean isThread(String url) {
        String std = standardize(url);
        if (std == null || !isSite(std)) return false;
        String low = std.toLowerCase();
        if (isSimple(low)) return P_SIMPLE_T.matcher(std).find();
        if (low.contains("read.php")) return P_TID_Q.matcher(std).find();
        return false;
    }

    /** 版块 / 分类列表页。 */
    public static boolean isBoard(String url) {
        String std = standardize(url);
        if (std == null || !isSite(std)) return false;
        String low = std.toLowerCase();
        if (isSimple(low)) return P_SIMPLE_F.matcher(std).find();
        if (low.contains("thread.php")) return P_FID_Q.matcher(std).find();
        return low.contains("cateid-") || low.contains("cateid=");
    }

    /** 首页（桌面版或移动版）。 */
    public static boolean isHome(String url) {
        String std = standardize(url);
        if (std == null || !isSite(std)) return false;
        String low = std.toLowerCase();
        if (isSimple(low)) {
            return !P_SIMPLE_T.matcher(std).find() && !P_SIMPLE_F.matcher(std).find();
        }
        String path;
        try {
            path = Uri.parse(std).getPath();
        } catch (Exception e) {
            path = null;
        }
        boolean rootPath = path == null || path.isEmpty()
                || "/".equals(path) || "/index.php".equals(path);
        return rootPath && !low.contains("cateid") && !low.contains("fid=");
    }

    public static String tid(String url) {
        String std = standardize(url);
        if (std == null) return null;
        if (isSimple(std)) {
            Matcher m = P_SIMPLE_T.matcher(std);
            return m.find() ? m.group(1) : null;
        }
        Matcher m = P_TID_Q.matcher(std);
        return m.find() ? m.group(1) : null;
    }

    /** 当前页码，默认 1。 */
    public static int pageOf(String url) {
        String std = standardize(url);
        if (std == null) return 1;
        if (isSimple(std)) {
            Matcher mt = P_SIMPLE_T.matcher(std);
            if (mt.find() && mt.group(2) != null) return parse(mt.group(2));
            Matcher mf = P_SIMPLE_F.matcher(std);
            if (mf.find() && mf.group(2) != null) return parse(mf.group(2));
        }
        Matcher m = P_PAGE.matcher(std);
        if (m.find()) return parse(m.group(1));
        m = P_PAGE_DASH.matcher(std);
        if (m.find()) return parse(m.group(1));
        m = P_DASH_PAGE.matcher(std);
        if (m.find()) return parse(m.group(1));
        return 1;
    }

    /** 同一帖子的判定：tid 相同且页码相同才算「同一页」。 */
    public static boolean sameThreadPage(String a, String b) {
        String ta = tid(a);
        String tb = tid(b);
        if (ta == null || tb == null) return false;
        return ta.equals(tb) && pageOf(a) == pageOf(b);
    }

    // ------------------------------------------------------------------ 地址构造

    public static String boardUrl(int fid, boolean mobile) {
        return boardUrl(fid, 1, mobile);
    }

    public static String boardUrl(int fid, int page, boolean mobile) {
        if (mobile) {
            return Site.base() + "/simple/index.php?f" + fid + pageSuffix(page) + ".html";
        }
        return Site.base() + "/thread.php?fid-" + fid
                + (page > 1 ? "-page-" + page : "") + ".html";
    }

    public static String threadUrl(String tid, boolean mobile) {
        if (mobile) return Site.base() + "/simple/index.php?t" + tid + ".html";
        return Site.base() + "/read.php?tid=" + tid;
    }

    private static String pageSuffix(int page) {
        return page > 1 ? "_" + page : "";
    }

    private static long firstLong(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? parseLong(m.group(1)) : -1L;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 1;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return -1L;
        }
    }
}
