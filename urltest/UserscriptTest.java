import net.northplus.app.Userscript;

import java.util.List;

/** Userscript 元数据解析与 @match 匹配的离线单测。 */
public class UserscriptTest {

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

    /** 凛+ 的真实头部（原本 markdown 吃掉的星号已还原）。 */
    private static final String RIN_HEADER =
            "// ==UserScript==\n"
            + "// @name         南加北加论坛强化脚本(凛+)\n"
            + "// @version      93\n"
            + "// @description  加强东南北+功能: 太多了请看功能简介\n"
            + "// @author       遠坂凛\n"
            + "// @namespace    tousakarin\n"
            + "// @license      MIT\n"
            + "// @grant        unsafeWindow\n"
            + "// @grant        GM.getValue\n"
            + "// @match        *://*.east-plus.net/*\n"
            + "// @match        *://east-plus.net/\n"
            + "// @match        *://*.south-plus.net/*\n"
            + "// @match        *://south-plus.net/\n"
            + "// @match        *://*.north-plus.net/*\n"
            + "// @match        *://north-plus.net/\n"
            + "// @match        *://*.imoutolove.me/*\n"
            + "// @match        *://imoutolove.me/\n"
            + "// @run-at       document-start\n"
            + "// ==/UserScript==\n"
            + "(function(){ \"use strict\"; const VERSION = 93; })();\n";

    public static void main(String[] args) {
        System.out.println("--- 头部解析（真实凛+ 头部）---");
        Userscript u = Userscript.parse(RIN_HEADER);
        eq("名称", u.name, "南加北加论坛强化脚本(凛+)");
        eq("版本", u.version, "93");
        eq("作者", u.author, "遠坂凛");
        eq("命名空间", u.namespace, "tousakarin");
        eq("运行时机", u.runAt, "document-start");
        eq("match 条数", u.matches.size(), 8);
        eq("grant 条数", u.grants.size(), 2);
        eq("源码保留", u.code.contains("const VERSION = 93"), true);

        System.out.println("--- 站点匹配 ---");
        eq("north-plus 主页", u.matches("https://north-plus.net/"), true);
        eq("north-plus 无路径", u.matches("https://north-plus.net"), true);
        eq("north-plus 移动版帖子",
                u.matches("https://north-plus.net/simple/index.php?t2086932.html"), true);
        eq("north-plus 桌面帖子", u.matches("https://north-plus.net/read.php?tid=2086932"), true);
        eq("www 子域", u.matches("https://www.south-plus.net/index.php"), true);
        eq("http 协议也算", u.matches("http://east-plus.net/a"), true);
        eq("站外不匹配", u.matches("https://example.com/"), false);
        eq("相似域名不匹配（防钓）", u.matches("https://evil-north-plus.net/x"), false);
        eq("后缀伪装不匹配", u.matches("https://north-plus.net.evil.com/x"), false);

        System.out.println("--- 边界与容错 ---");
        eq("缺失前导星号的 @match", Userscript.matchPattern("://north-plus.net/*",
                "https://north-plus.net/a"), true);
        eq("纯路径通配", Userscript.matchPattern("*://north-plus.net/simple/*",
                "https://north-plus.net/simple/index.php?t1.html"), true);
        eq("路径通配不越界", Userscript.matchPattern("*://north-plus.net/simple/*",
                "https://north-plus.net/read.php?tid=1"), false);
        eq("主机全通配", Userscript.matchPattern("*://*/*", "https://anything.io/x"), true);
        eq("非法模式不崩", Userscript.matchPattern("这不是模式", "https://a.com/"), false);
        eq("空 URL 不崩", Userscript.matchPattern("*://*/*", ""), false);

        System.out.println("--- @include 三种写法 ---");
        eq("include 正则", Userscript.matchInclude("/read\\.php\\?tid=\\d+/",
                "https://north-plus.net/read.php?tid=5"), true);
        eq("include 通配", Userscript.matchInclude("*://north-plus.net/simple/*",
                "https://north-plus.net/simple/index.php"), true);
        eq("include 普通串", Userscript.matchInclude("https://north-plus.net/u.php",
                "https://north-plus.net/u.php"), true);

        System.out.println("--- @exclude 优先于 @match ---");
        String src = "// ==UserScript==\n"
                + "// @name 排除测试\n"
                + "// @match *://*.north-plus.net/*\n"
                + "// @exclude *://*.north-plus.net/simple/*\n"
                + "// ==/UserScript==\n";
        Userscript ex = Userscript.parse(src);
        eq("被排除的移动版", ex.matches("https://north-plus.net/simple/index.php?f9.html"), false);
        eq("未被排除的桌面版", ex.matches("https://north-plus.net/read.php?tid=1"), true);

        System.out.println("--- id 稳定性（升级要落到同一个 id）---");
        Userscript a = Userscript.parse(RIN_HEADER);
        Userscript b = Userscript.parse(RIN_HEADER.replace("// @version      93", "// @version      95"));
        eq("同脚本不同版本 id 一致", a.id.equals(b.id), true);
        eq("id 带可读后缀", a.id.contains("_"), true);
        System.out.println("        id = " + a.id);

        String other = RIN_HEADER.replace("南加北加论坛强化脚本(凛+)", "另一个脚本");
        eq("不同脚本 id 不同", Userscript.parse(other).id.equals(a.id), false);

        System.out.println("--- 无头部文件不应被当成脚本 ---");
        Userscript plain = Userscript.parse("just some javascript");
        eq("无 match 规则", plain.matches.size(), 0);
        eq("无 match 则不匹配任何地址", plain.matches("https://north-plus.net/"), false);

        List<String> ms = a.matches;
        System.out.println("        解析出的 match: " + ms.size() + " 条，首条 = " + ms.get(0));

        System.out.println();
        System.out.println("结果: PASS=" + pass + "  FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }
}
