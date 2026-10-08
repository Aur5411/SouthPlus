import net.northplus.app.UserscriptSources;

import java.util.List;

/** 用户脚本多源下载候选生成的离线单测。 */
public class UserscriptSourcesTest {

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

    private static void has(String label, String haystack, String needle) {
        boolean ok = haystack != null && haystack.contains(needle);
        if (ok) {
            pass++;
            System.out.println("  PASS  " + label);
        } else {
            fail++;
            System.out.println("  FAIL  " + label + "  ->  [" + haystack + "] 不含 [" + needle + "]");
        }
    }

    private static void hasNot(String label, String haystack, String needle) {
        boolean ok = haystack == null || !haystack.contains(needle);
        if (ok) {
            pass++;
            System.out.println("  PASS  " + label);
        } else {
            fail++;
            System.out.println("  FAIL  " + label + "  ->  [" + haystack + "] 不该含 [" + needle + "]");
        }
    }

    private static final String NAME = "南加北加论坛强化脚本(凛+)";

    public static void main(String[] args) {
        System.out.println("--- 源清单 ---");
        eq("源数量", UserscriptSources.SOURCES.length, 5);
        eq("首个是奇趣镜像", UserscriptSources.SOURCES[0].pageHost, "gf.qytechs.cn");
        eq("首个是国内的", UserscriptSources.SOURCES[0].domestic, true);
        eq("最后一个是官方", UserscriptSources.SOURCES[UserscriptSources.OFFICIAL_INDEX].pageHost,
                "greasyfork.org");
        eq("官方不是国内的",
                UserscriptSources.SOURCES[UserscriptSources.OFFICIAL_INDEX].domestic, false);
        int domestic = 0;
        for (UserscriptSources.Source s : UserscriptSources.SOURCES) if (s.domestic) domestic++;
        eq("国内镜像有 4 个", domestic, 4);

        System.out.println("--- 脚本 ID 提取 ---");
        eq("发布页", UserscriptSources.scriptId("https://greasyfork.org/zh-CN/scripts/454120"),
                "454120");
        eq("直链", UserscriptSources.scriptId(
                "https://update.greasyfork.org/scripts/454120/xxx.user.js"), "454120");
        eq("镜像发布页", UserscriptSources.scriptId("https://gf.qytechs.cn/zh-CN/scripts/454120"),
                "454120");
        eq("无 ID 返回 null", UserscriptSources.scriptId("https://example.com/foo"), null);
        eq("null 输入", UserscriptSources.scriptId(null), null);

        System.out.println("--- 主机替换 ---");
        eq("发布页官方 → 奇趣",
                UserscriptSources.rewrite("https://greasyfork.org/zh-CN/scripts/454120",
                        UserscriptSources.SOURCES[0]),
                "https://gf.qytechs.cn/zh-CN/scripts/454120");
        eq("直链官方 → greasyfork.cc",
                UserscriptSources.rewrite(
                        "https://update.greasyfork.org/scripts/454120/a.user.js",
                        UserscriptSources.byLabel("greasyfork.cc")),
                "https://update.greasyfork.cc/scripts/454120/a.user.js");
        eq("反向：奇趣 → 官方",
                UserscriptSources.rewrite("https://gf.qytechs.cn/zh-CN/scripts/1",
                        UserscriptSources.byLabel("官方源")),
                "https://greasyfork.org/zh-CN/scripts/1");
        eq("同源不改", UserscriptSources.rewrite("https://gf.qytechs.cn/x",
                UserscriptSources.SOURCES[0]), "https://gf.qytechs.cn/x");
        eq("无路径也能换",
                UserscriptSources.rewrite("https://greasyfork.org", UserscriptSources.SOURCES[0]),
                "https://gf.qytechs.cn");
        eq("带端口/查询",
                UserscriptSources.rewrite("https://greasyfork.org/a?b=1#c",
                        UserscriptSources.SOURCES[0]),
                "https://gf.qytechs.cn/a?b=1#c");

        System.out.println("--- 主机边界（防误伤相似域名）---");
        eq("前缀伪装不换",
                UserscriptSources.rewrite("https://evil-greasyfork.org/x",
                        UserscriptSources.SOURCES[0]),
                "https://evil-greasyfork.org/x");
        eq("后缀伪装不换",
                UserscriptSources.rewrite("https://greasyfork.org.evil.com/x",
                        UserscriptSources.SOURCES[0]),
                "https://greasyfork.org.evil.com/x");
        eq("非源地址不换",
                UserscriptSources.rewrite("https://example.com/x",
                        UserscriptSources.SOURCES[0]),
                "https://example.com/x");

        System.out.println("--- 源主机识别 ---");
        eq("官方", UserscriptSources.isSourceHost("https://greasyfork.org/a"), true);
        eq("更新域", UserscriptSources.isSourceHost("https://update.greasyfork.org/a"), true);
        eq("镜像", UserscriptSources.isSourceHost("https://gf.qytechs.cn/a"), true);
        eq("站外", UserscriptSources.isSourceHost("https://example.com/a"), false);

        System.out.println("--- 直链安装候选 ---");
        List<String[]> c = UserscriptSources.installCandidates("454120", NAME);
        eq("候选数量 = 源数 × 2", c.size(), 10);
        has("第一个是国内镜像", c.get(0)[0], "https://update.gf.qytechs.cn/scripts/454120/");
        has("脚本名已 URL 编码", c.get(0)[0], "%E5%8D%97%E5%8A%A0%E5%8C%97");
        has("括号编码正确", c.get(0)[0], "%28");
        has("加号编码正确", c.get(0)[0], "%2B");
        eq("第一个候选的来源标签", c.get(0)[1], "奇趣镜像");
        has("第二个是简写形式", c.get(1)[0], "https://update.gf.qytechs.cn/scripts/454120.user.js");
        has("最后一个含官方域", c.get(c.size() - 1)[0], "update.greasyfork.org");
        System.out.println("        样例: " + c.get(0)[0]);

        List<String[]> cNoName = UserscriptSources.installCandidates("454120", null);
        eq("无脚本名时只有简写形式", cNoName.size(), 5);

        System.out.println("--- 直链地址扩展 ---");
        List<String[]> d = UserscriptSources.directCandidates(
                "https://update.greasyfork.org/scripts/454120/a.user.js");
        eq("扩展到 5 个源", d.size(), 5);
        has("含奇趣", d.get(0)[0], "update.gf.qytechs.cn");
        has("含官方", d.get(4)[0], "update.greasyfork.org");
        eq("非源地址去重后仍 5 条但内容相同",
                UserscriptSources.directCandidates("https://example.com/a.user.js").get(0)[0],
                "https://example.com/a.user.js");

        System.out.println("--- 发布页候选 ---");
        List<String[]> p = UserscriptSources.pageCandidates(454120);
        eq("发布页候选 5 条", p.size(), 5);
        eq("首个", p.get(0)[0], "https://gf.qytechs.cn/zh-CN/scripts/454120");
        eq("末个", p.get(4)[0], "https://greasyfork.org/zh-CN/scripts/454120");

        System.out.println("--- 从发布页 HTML 解析安装链接 ---");
        String page = "https://gf.qytechs.cn/zh-CN/scripts/454120";
        String htmlAbs = "<a class=\"install-link\" href=\"https://update.gf.qytechs.cn/"
                + "scripts/454120/x.user.js\">安装</a>";
        eq("绝对链接", UserscriptSources.discoverInstallLink(page, htmlAbs, true),
                "https://update.gf.qytechs.cn/scripts/454120/x.user.js");

        String htmlRel = "<a href=\"/scripts/454120/x.user.js\">安装</a>";
        eq("相对链接补全主机", UserscriptSources.discoverInstallLink(page, htmlRel, true),
                "https://gf.qytechs.cn/scripts/454120/x.user.js");

        String htmlOfficial = "<a href=\"https://update.greasyfork.org/scripts/454120/x.user.js\">i</a>";
        eq("页面给官方链接时换回镜像",
                UserscriptSources.discoverInstallLink(page, htmlOfficial, true),
                "https://update.gf.qytechs.cn/scripts/454120/x.user.js");

        eq("没有安装链接返回 null",
                UserscriptSources.discoverInstallLink(page, "<a href=\"/x.html\">y</a>", true),
                null);
        eq("null html", UserscriptSources.discoverInstallLink(page, null, true), null);

        System.out.println();
        System.out.println("结果: PASS=" + pass + "  FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }
}
