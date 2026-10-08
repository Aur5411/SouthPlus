import net.northplus.app.Site;
import net.northplus.app.UrlMapper;

/** 多站点候选、域名归一化与切换后地址改写的离线单测。 */
public class SiteTest {

    private static int pass = 0;
    private static int fail = 0;

    private static void eq(String label, Object actual, Object expected) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) {
            pass++;
            System.out.println("  PASS  " + label);
        } else {
            fail++;
            System.out.println("  FAIL  " + label + "  ->  期望 [" + expected + "] 实际 [" + actual + "]");
        }
    }

    public static void main(String[] args) {
        System.out.println("--- 内置站点清单 ---");
        eq("候选数", Site.BUILT_IN.length, 10);
        eq("默认站点", Site.BUILT_IN[0].host, "north-plus.net");
        eq("默认叫法", Site.BUILT_IN[0].label, "北+");
        eq("初始生效域名", Site.host(), "north-plus.net");
        String[][] expect = {
                {"north-plus.net", "北+"}, {"soul-plus.net", "魂+"}, {"south-plus.net", "南+"},
                {"white-plus.net", "白+"}, {"level-plus.net", "Lv+"}, {"summer-plus.net", "夏+"},
                {"spring-plus.net", "春+"}, {"snow-plus.net", "雪+"}, {"east-plus.net", "东+"},
                {"blue-plus.net", "蓝+"},
        };
        for (int i = 0; i < expect.length; i++) {
            eq("第 " + (i + 1) + " 项 " + expect[i][1], Site.BUILT_IN[i].host, expect[i][0]);
            eq("第 " + (i + 1) + " 项叫法", Site.BUILT_IN[i].label, expect[i][1]);
        }

        System.out.println("--- 域名归一化 ---");
        eq("纯域名", Site.setHost("north-plus.net") && Site.host().equals("north-plus.net"), true);
        eq("带协议与路径",
                Site.setHost("https://south-plus.net/index.php") && Site.host().equals("south-plus.net"),
                true);
        eq("带 www",
                Site.setHost("http://www.blue-plus.net/x") && Site.host().equals("www.blue-plus.net"),
                true);
        eq("大小写与空格",
                Site.setHost("  Soul-Plus.NET  ") && Site.host().equals("soul-plus.net"), true);
        eq("带端口",
                Site.setHost("level-plus.net:8443/a") && Site.host().equals("level-plus.net:8443"),
                true);
        eq("非法（无点）被拒", Site.setHost("localhost"), false);
        eq("非法（空）被拒", Site.setHost("   "), false);
        eq("非法后域名未变", Site.host(), "level-plus.net:8443");

        System.out.println("--- 内置判定与展示名 ---");
        eq("裸域是内置", Site.isBuiltIn("south-plus.net"), true);
        eq("带 www 也算内置", Site.isBuiltIn("www.south-plus.net"), true);
        eq("自定义域不是内置", Site.isBuiltIn("example.com"), false);
        eq("null 安全", Site.isBuiltIn(null), false);
        eq("展示名 北+", Site.displayName("north-plus.net"), "北+");
        eq("展示名 自定义回退", Site.displayName("example.com"), "example.com");

        System.out.println("--- 切换站点后地址改写 ---");
        Site.setHost("north-plus.net");
        eq("切回默认", Site.host(), "north-plus.net");
        eq("默认域下 family 都算本站",
                UrlMapper.isSite("https://soul-plus.net/read.php?tid=1"), true);
        eq("默认域下改写镜像",
                UrlMapper.standardize("https://soul-plus.net/read.php?tid-1.html"),
                "https://north-plus.net/read.php?tid-1.html");

        Site.setHost("soul-plus.net");
        eq("切到 魂+", Site.host(), "soul-plus.net");
        eq("base 跟随", Site.base(), "https://soul-plus.net");
        eq("首页跟随", Site.home(), "https://soul-plus.net/index.php");
        eq("移动首页跟随", Site.homeMobile(), "https://soul-plus.net/simple/");
        eq("搜索页跟随", Site.search(), "https://soul-plus.net/search.php");
        eq("北+ 仍算本站",
                UrlMapper.isSite("https://north-plus.net/read.php?tid=1"), true);
        eq("北+ 被改写为当前域",
                UrlMapper.standardize("https://north-plus.net/read.php?tid-9.html"),
                "https://soul-plus.net/read.php?tid-9.html");
        eq("当前域自身不改写",
                UrlMapper.standardize("https://soul-plus.net/read.php?tid=1"),
                "https://soul-plus.net/read.php?tid=1");
        eq("站外不改写",
                UrlMapper.standardize("https://example.com/a"), "https://example.com/a");
        eq("构造的版块地址用当前域",
                UrlMapper.boardUrl(9, false), "https://soul-plus.net/thread.php?fid-9.html");
        eq("移动版地址转换用当前域",
                UrlMapper.toMobile("https://soul-plus.net/thread.php?fid-9.html"),
                "https://soul-plus.net/simple/index.php?f9.html");

        Site.setHost("north-plus.net");
        eq("复原默认", Site.host(), "north-plus.net");
        eq("复原后构造地址", UrlMapper.boardUrl(9, false),
                "https://north-plus.net/thread.php?fid-9.html");

        System.out.println();
        System.out.println("结果: PASS=" + pass + "  FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }
}
