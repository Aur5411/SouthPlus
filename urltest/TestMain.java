import net.northplus.app.Const;
import net.northplus.app.UrlMapper;

public class TestMain {

    private static int pass = 0;
    private static int fail = 0;

    private static void eq(String label, String actual, String expected) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        report(ok, label, "期望 [" + expected + "] 实际 [" + actual + "]");
    }

    private static void eq(String label, boolean actual, boolean expected) {
        report(actual == expected, label, "期望 " + expected + " 实际 " + actual);
    }

    private static void eq(String label, int actual, int expected) {
        report(actual == expected, label, "期望 " + expected + " 实际 " + actual);
    }

    private static void report(boolean ok, String label, String detail) {
        if (ok) {
            pass++;
            System.out.println("  PASS  " + label);
        } else {
            fail++;
            System.out.println("  FAIL  " + label + "  ->  " + detail);
        }
    }

    public static void main(String[] args) {
        final String B = "https://north-plus.net";
        final String HOMED = B + "/index.php";
        final String HOMEM = B + "/simple/";

        System.out.println("--- 默认入口与 UA ---");
        eq("默认首页是电脑版（桌面）", UrlMapper.toDesktop(HOMEM), HOMED);
        eq("桌面首页就是 /index.php", HOMED.endsWith("/index.php"), true);
        eq("默认 UA 是桌面 Chrome", Const.UA.contains("Windows NT 10.0"), true);
        eq("默认 UA 不含 Mobile", Const.UA.contains("Mobile"), false);
        eq("移动 UA 才带 Mobile", Const.UA_MOBILE.contains("Mobile"), true);

        System.out.println("--- 镜像域识别 ---");
        for (String h : new String[]{"south-plus.net", "www.blue-plus.net", "level-plus.net",
                "soul-plus.net", "snow-plus.org", "spring-plus.net", "summer-plus.net",
                "white-plus.net", "east-plus.net", "north-plus.net"}) {
            eq("同族域 " + h, UrlMapper.isSite("https://" + h + "/read.php?tid=1"), true);
        }
        eq("站外域不算本站", UrlMapper.isSite("https://example.com/"), false);
        eq("相似域不算本站", UrlMapper.isSite("https://evil-north-plus.net/"), false);

        System.out.println("--- 镜像域改写（保住当前域登录态）---");
        eq("blue-plus 帖子 → north-plus",
                UrlMapper.standardize("https://www.blue-plus.net/read.php?tid-2038640.html"),
                B + "/read.php?tid-2038640.html");
        eq("level-plus 版块 → north-plus",
                UrlMapper.standardize("https://level-plus.net/thread.php?fid-9.html"),
                B + "/thread.php?fid-9.html");
        eq("主站自身不改写",
                UrlMapper.standardize(B + "/read.php?tid=1"), B + "/read.php?tid=1");
        eq("站外不改写",
                UrlMapper.standardize("https://example.com/a"), "https://example.com/a");
        eq("协议相对地址绝对化",
                UrlMapper.standardize("//north-plus.net/u.php"), B + "/u.php");
        eq("镜像域根路径",
                UrlMapper.standardize("https://www.south-plus.net/"), B + "/");

        System.out.println("--- 电脑版 → 移动版（仅在移动模式下用）---");
        eq("桌面首页 → 移动首页", UrlMapper.toMobile(B + "/"), HOMEM);
        eq("index.php → 移动首页", UrlMapper.toMobile(B + "/index.php"), HOMEM);
        eq("无路径 → 移动首页", UrlMapper.toMobile(B), HOMEM);
        eq("read.php?tid=", UrlMapper.toMobile(B + "/read.php?tid=277789"),
                B + "/simple/index.php?t277789.html");
        eq("read.php?tid-", UrlMapper.toMobile(B + "/read.php?tid-277789.html"),
                B + "/simple/index.php?t277789.html");
        eq("帖子翻页", UrlMapper.toMobile(B + "/read.php?tid=277789&page=2"),
                B + "/simple/index.php?t277789_2.html");
        eq("thread.php?fid-", UrlMapper.toMobile(B + "/thread.php?fid-9.html"),
                B + "/simple/index.php?f9.html");
        eq("版块翻页 -page-", UrlMapper.toMobile(B + "/thread.php?fid-9-page-3.html"),
                B + "/simple/index.php?f9_3.html");
        eq("thread.php?fid=", UrlMapper.toMobile(B + "/thread.php?fid=9"),
                B + "/simple/index.php?f9.html");
        eq("镜像域桌面帖也转移动版",
                UrlMapper.toMobile("https://www.blue-plus.net/read.php?tid-42.html"),
                B + "/simple/index.php?t42.html");
        eq("移动版保持原样", UrlMapper.toMobile(HOMEM), HOMEM);
        eq("移动版版块保持原样", UrlMapper.toMobile(B + "/simple/index.php?f9_2.html"),
                B + "/simple/index.php?f9_2.html");
        eq("搜索页无移动对应，保持", UrlMapper.toMobile(B + "/search.php?keyword=a"),
                B + "/search.php?keyword=a");
        eq("登录页保持", UrlMapper.toMobile(B + "/login.php"), B + "/login.php");

        System.out.println("--- 移动版 → 电脑版（切换用）---");
        eq("移动首页 → 桌面首页", UrlMapper.toDesktop(HOMEM), HOMED);
        eq("移动帖 → 桌面帖", UrlMapper.toDesktop(B + "/simple/index.php?t277789.html"),
                B + "/read.php?tid=277789");
        eq("移动帖翻页 → 桌面帖翻页",
                UrlMapper.toDesktop(B + "/simple/index.php?t277789_2.html"),
                B + "/read.php?tid=277789&page=2");
        eq("移动版块 → 桌面版块", UrlMapper.toDesktop(B + "/simple/index.php?f9.html"),
                B + "/thread.php?fid-9.html");
        eq("移动版块翻页 → 桌面版块翻页",
                UrlMapper.toDesktop(B + "/simple/index.php?f9_3.html"),
                B + "/thread.php?fid-9-page-3.html");
        eq("桌面地址本身不变", UrlMapper.toDesktop(B + "/thread.php?fid-9.html"),
                B + "/thread.php?fid-9.html");
        eq("搜索页不变", UrlMapper.toDesktop(B + "/search.php"), B + "/search.php");

        System.out.println("--- 页面类型判定（两套形态都要认）---");
        eq("帖子(桌面)", UrlMapper.isThread(B + "/read.php?tid=2038640"), true);
        eq("帖子(桌面 -page-)", UrlMapper.isThread(B + "/read.php?tid-2038640-page-2.html"), true);
        eq("帖子(移动)", UrlMapper.isThread(B + "/simple/index.php?t2978617_2.html"), true);
        eq("镜像域帖子", UrlMapper.isThread("https://www.blue-plus.net/read.php?tid-1.html"), true);
        eq("版块不是帖子", UrlMapper.isThread(B + "/thread.php?fid-9.html"), false);
        eq("版块(桌面)", UrlMapper.isBoard(B + "/thread.php?fid-9.html"), true);
        eq("版块(桌面翻页)", UrlMapper.isBoard(B + "/thread.php?fid-9-page-2.html"), true);
        eq("版块(移动)", UrlMapper.isBoard(B + "/simple/index.php?f9_2.html"), true);
        eq("分类页算版块", UrlMapper.isBoard(B + "/index.php?cateid-7.html"), true);
        eq("帖子不是版块", UrlMapper.isBoard(B + "/read.php?tid=1"), false);

        System.out.println("--- 首页判定 ---");
        eq("桌面首页 /", UrlMapper.isHome(B + "/"), true);
        eq("桌面首页 /index.php", UrlMapper.isHome(HOMED), true);
        eq("移动首页", UrlMapper.isHome(HOMEM), true);
        eq("移动首页 index.php", UrlMapper.isHome(B + "/simple/index.php"), true);
        eq("分类页不是首页", UrlMapper.isHome(B + "/index.php?cateid-7.html"), false);
        eq("版块页不是首页", UrlMapper.isHome(B + "/thread.php?fid-9.html"), false);
        eq("帖子页不是首页", UrlMapper.isHome(B + "/read.php?tid=1"), false);

        System.out.println("--- tid / 页码 / 同帖判定 ---");
        eq("tid(桌面)", UrlMapper.tid(B + "/read.php?tid=2038640"), "2038640");
        eq("tid(桌面 -page-)", UrlMapper.tid(B + "/read.php?tid-2038640-page-3.html"), "2038640");
        eq("tid(移动)", UrlMapper.tid(B + "/simple/index.php?t2978617_2.html"), "2978617");
        eq("页码 -page-", UrlMapper.pageOf(B + "/thread.php?fid-9-page-2.html"), 2);
        eq("页码 &page=", UrlMapper.pageOf(B + "/read.php?tid=1&page=4"), 4);
        eq("页码 fpage 不误判", UrlMapper.pageOf(B + "/read.php?tid-2932723-fpage-2.html"), 1);
        eq("页码 _n", UrlMapper.pageOf(B + "/simple/index.php?t2978617_2.html"), 2);
        eq("页码默认 1", UrlMapper.pageOf(B + "/thread.php?fid-9.html"), 1);
        eq("同帖同页（跨形态）",
                UrlMapper.sameThreadPage(B + "/read.php?tid=123",
                        B + "/simple/index.php?t123.html"), true);
        eq("同帖不同页",
                UrlMapper.sameThreadPage(B + "/read.php?tid=123",
                        B + "/read.php?tid-123-page-2.html"), false);
        eq("不同帖不算同页",
                UrlMapper.sameThreadPage(B + "/read.php?tid=123", B + "/read.php?tid=456"), false);

        System.out.println("--- 地址构造 ---");
        eq("boardUrl 电脑版", UrlMapper.boardUrl(9, false), B + "/thread.php?fid-9.html");
        eq("boardUrl 电脑版翻页", UrlMapper.boardUrl(9, 3, false),
                B + "/thread.php?fid-9-page-3.html");
        eq("boardUrl 移动版", UrlMapper.boardUrl(9, true), B + "/simple/index.php?f9.html");
        eq("boardUrl 移动版翻页", UrlMapper.boardUrl(9, 3, true),
                B + "/simple/index.php?f9_3.html");
        eq("threadUrl 电脑版", UrlMapper.threadUrl("2038640", false),
                B + "/read.php?tid=2038640");
        eq("threadUrl 移动版", UrlMapper.threadUrl("2038640", true),
                B + "/simple/index.php?t2038640.html");

        System.out.println();
        System.out.println("结果: PASS=" + pass + "  FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }
}
