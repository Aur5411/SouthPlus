package net.northplus.app;

/**
 * 与具体站点无关的常量。
 *
 * <p>凡是跟域名绑定的值（首页、用户中心、搜索页、Referer 等）都走 {@link Site}，
 * 因为站点域名是运行时可切换的。这里只留 UA、Intent 约定与层级上限。
 */
public final class Const {

    /** 桌面 Chrome UA —— 默认使用，站点据此返回电脑版页面。 */
    public static final String UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    /** 移动版 UA，仅在「移动版网页」开关打开时使用。 */
    public static final String UA_MOBILE =
            "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    /** 默认 UA 别名，供图片 / 用户脚本下载等非页面请求使用。 */
    public static final String UA = UA_DESKTOP;

    public static final String EXTRA_URL = "np_url";
    public static final String EXTRA_DEPTH = "np_depth";

    /** 界面堆叠硬上限，防止「分类→帖子→分类→帖子」无限展开。 */
    public static final int MAX_DEPTH = 2;

    private Const() {
    }
}
