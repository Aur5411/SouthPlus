package net.northplus.app;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebView 外壳基类。
 *
 * <p>导航分层模型（对应站点结构）：
 * <pre>
 *   第 0 层 MainActivity   首页 / 版块列表 —— 列表自身翻页留在本层
 *   第 1 层 ThreadActivity 帖子           —— 独立层，返回时下层列表原样保留
 *   第 2 层 ThreadActivity 帖子里的版块列表 → 再开帖子（受 MAX_DEPTH 限制）
 * </pre>
 * 之所以要分层：Android WebView 没有 BFCache，{@code goBack()} 必然重建文档，
 * 站点把后续页 JS append 进当前 DOM 时会被丢弃。让列表所在的界面只被覆盖、不被销毁，
 * 是唯一可靠的做法。
 */
public abstract class BaseWebActivity extends AppCompatActivity implements WebBridge.Host {

    private static final int REQ_FILE = 0x51;

    private static final int MENU_SITE = 1;
    private static final int MENU_COPY = 2;
    private static final int MENU_SETTINGS = 3;
    private static final int MENU_SCRIPTS = 4;

    /** 1×1 透明 GIF，用于替换被拦截的广告图，避免出现破图图标。 */
    private static final byte[] TRANSPARENT_GIF = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00, (byte) 0x80, 0x00,
            0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x21, (byte) 0xF9,
            0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00,
            0x01, 0x00, 0x00, 0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
    };

    private static final String[] EXTERNAL_SCHEMES = {
            "mailto:", "tel:", "sms:", "magnet:", "thunder:", "ed2k:", "file:",
            "mqqapi:", "weixin:", "alipays:", "alipay:", "taobao:", "tmall:",
            "intent:", "market:", "baiduyun:", "bdpan:", "dingtalk:", "tg:", "line:",
            "snssdk", "bilibili:", "youku:", "iqiyi", "pptv", "tenvideo", "sinaweibo"
    };

    protected WebView web;
    protected SwipeRefreshLayout swipe;
    protected ProgressBar progress;
    protected TextView pageTitle;
    protected FrameLayout content;
    protected LinearLayout bottomNav;
    protected View navDivider;
    protected View errorView;
    protected TextView errorDetail;

    protected final Prefs prefs = App.prefs;

    /** 当前主文档地址，onPageStarted 里持续更新，用于分层判定。 */
    protected String lastContentUrl = "";
    /** 本层入口地址。 */
    protected String entryUrl = "";

    private long lastBackPress = 0L;
    private boolean desktopMode = false;
    private boolean pageLoadError = false;
    private ValueCallback<Uri[]> pendingFileCallback;

    /** 用户脚本宿主。 */
    private ScriptBridge scriptBridge;
    private androidx.webkit.ScriptHandler documentStartHandler;
    private boolean hostInjectOnPageStarted = false;
    private static String sHostJs;

    /** 本界面创建时使用的站点域名，用于检测设置里换过站点。 */
    private String hostAtCreate;

    /** 脚本通过 GM_registerMenuCommand 注册的命令：commandId -> 标题。 */
    private final LinkedHashMap<String, String> scriptMenus = new LinkedHashMap<>();

    // ---------------------------------------------------------------- 子类契约

    /** 首次加载的地址（桌面版地址会被自动转成移动版）。 */
    protected abstract String initialUrl();

    /** 是否主界面：主界面不做「退到入口即关闭本界面」处理。 */
    protected abstract boolean rootScreen();

    /** 本层深度，0 为主界面。 */
    protected int screenDepth() {
        return rootScreen() ? 0 : 1;
    }

    /** 当前是否为电脑版模式（UA 与页面版本都由它决定）。 */
    protected boolean isDesktopMode() {
        return desktopMode;
    }

    /** 布局就绪后的扩展点。 */
    protected void onShellReady() {
    }

    /**
     * 站外链接是否也留在本界面内浏览。
     * 脚本安装页（Greasyfork 之类）需要这个：点「安装此脚本」时必须还停在应用内，
     * 才能被 {@code .user.js} 拦截逻辑接管。
     */
    protected boolean inAppBrowsing() {
        return false;
    }

    // ---------------------------------------------------------------- 生命周期

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        web = findViewById(R.id.web);
        swipe = findViewById(R.id.swipe);
        progress = findViewById(R.id.progress);
        pageTitle = findViewById(R.id.pageTitle);
        content = findViewById(R.id.content);
        bottomNav = findViewById(R.id.bottomNav);
        navDivider = findViewById(R.id.navDivider);
        errorView = findViewById(R.id.errorView);
        errorDetail = findViewById(R.id.errorDetail);

        String saved = savedInstanceState != null ? savedInstanceState.getString("np_entry") : null;
        desktopMode = !prefs.mobileMode();
        if (!TextUtils.isEmpty(saved)) {
            entryUrl = saved;
        } else {
            entryUrl = UrlMapper.standardize(initialUrl());
            if (!desktopMode) entryUrl = UrlMapper.toMobile(entryUrl);
        }
        lastContentUrl = entryUrl;

        setupWebView();
        setupToolbar();
        setupSwipe();

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(entryUrl);
        }

        onShellReady();

        hostAtCreate = Site.host();
        if (rootScreen() && !prefs.siteAutoPicked()) {
            autoPickSite();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("np_entry", entryUrl);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 必须放在任何提前 return 之前，否则界面会停在暂停态
        if (web != null) web.onResume();
        // 设置里改过「移动版网页」→ 重建一次以套用新的 UA 与页面版本
        if (desktopMode == prefs.mobileMode()) {
            recreate();
            return;
        }
        // 设置里换过「访问站点」→ 按新域名重新加载
        if (hostAtCreate == null) hostAtCreate = Site.host();
        if (!hostAtCreate.equalsIgnoreCase(Site.host())) {
            hostAtCreate = Site.host();
            reloadForNewSite();
        }

        if (prefs.scriptsEnabled()) {
            if (hostInjectOnPageStarted || documentStartHandler == null) {
                boolean wasRegistered = hostInjectOnPageStarted || documentStartHandler != null;
                registerUserscriptHost();
                if (!wasRegistered && web != null) web.reload();
            }
        }
        inject();
    }

    // ---------------------------------------------------------------- 访问站点

    /** 换域名后按新域名重新生成入口地址并加载。 */
    private void reloadForNewSite() {
        if (web == null) return;
        entryUrl = UrlMapper.standardize(initialUrl());
        if (!desktopMode) entryUrl = UrlMapper.toMobile(entryUrl);
        lastContentUrl = entryUrl;
        web.loadUrl(entryUrl);
        toast("已切换到 " + Site.displayName(Site.host()) + "　" + Site.host());
    }

    /** 首次启动时探测一次，优先用能直连的站点。 */
    private void autoPickSite() {
        SiteProbe.pickReachable((index, host) -> {
            if (isFinishing() || host == null) return;
            // 找到了可达站点才算完成过自动选择；全不可达就留到下次再试
            prefs.setSiteAutoPicked(true);
            if (host.equalsIgnoreCase(Site.host())) return;
            Site.setHost(host);
            prefs.setSiteHost(Site.host());
            hostAtCreate = Site.host();
            reloadForNewSite();
        });
    }

    /** 加载失败时手动换一个可用站点。 */
    private void switchToReachableSite() {
        toast("正在寻找可直连的站点…");
        SiteProbe.pickReachable((index, host) -> {
            if (isFinishing()) return;
            if (host == null) {
                toast(getString(R.string.site_none_reachable));
                return;
            }
            if (host.equalsIgnoreCase(Site.host())) {
                reload();
                return;
            }
            Site.setHost(host);
            prefs.setSiteHost(Site.host());
            hostAtCreate = Site.host();
            reloadForNewSite();
        });
    }

    @Override
    protected void onDestroy() {
        if (documentStartHandler != null) {
            try {
                documentStartHandler.remove();
            } catch (Exception ignored) {
            }
            documentStartHandler = null;
        }
        if (web != null) {
            View parent = (View) web.getParent();
            if (parent instanceof SwipeRefreshLayout) {
                ((SwipeRefreshLayout) parent).removeView(web);
            }
            web.stopLoading();
            web.setWebChromeClient(null);
            web.setWebViewClient(null);
            web.removeJavascriptInterface("NP");
            web.removeJavascriptInterface("NP_SCRIPT");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- WebView

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(prefs.pinchZoom());
        s.setDisplayZoomControls(false);
        s.setSupportZoom(prefs.pinchZoom());
        s.setUserAgentString(desktopMode ? Const.UA_DESKTOP : Const.UA_MOBILE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setDefaultTextEncodingName("UTF-8");
        s.setTextZoom(prefs.fontScale(this));
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSaveFormData(true);

        web.setBackgroundColor(ContextCompat.getColor(this,
                prefs.night() ? R.color.np_bg : R.color.np_bar));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new WebBridge(this), "NP");
        web.addJavascriptInterface(new ScriptBridge(this), "NP_SCRIPT");
        registerUserscriptHost();
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener(downloadListener);
    }

    // ---------------------------------------------------------------- 用户脚本宿主

    /**
     * 注册用户脚本宿主。
     *
     * <p>优先用 androidx.webkit 的 document-start 注入（时序与油猴一致）；
     * 设备不支持时退化为 onPageStarted 注入——晚一点点，但功能不受影响。
     */
    private void registerUserscriptHost() {
        if (!prefs.scriptsEnabled()) return;
        String js = hostJs();
        if (js.isEmpty()) return;
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
                documentStartHandler =
                        androidx.webkit.WebViewCompat.addDocumentStartJavaScript(
                                web, js, Collections.singleton("*"));
                return;
            }
        } catch (Throwable ignored) {
            // 落到下面的兜底路径
        }
        hostInjectOnPageStarted = true;
    }

    private String hostJs() {
        if (sHostJs == null) {
            sHostJs = readAsset("userscripts/host.js");
        }
        return sHostJs;
    }

    private String readAsset(String path) {
        try (InputStream in = getAssets().open(path)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 原生兜底执行脚本：不经过 {@code new Function}，因此不受页面 CSP 限制。
     * 用函数参数绑定 GM，保证多脚本同时运行时各自的存储互不串号。
     */
    public void runScriptNatively(String id) {
        if (web == null || TextUtils.isEmpty(id)) return;
        UserscriptStore store = UserscriptStore.get(this);
        String code = store.codeOf(id);
        if (TextUtils.isEmpty(code)) return;
        Userscript u = store.byId(id);
        String name = u == null ? "" : u.name;
        String version = u == null ? "" : u.version;

        String js = "(function(){try{"
                + "var __g=window.__npMakeGM&&window.__npMakeGM(" + quote(id) + ");"
                + "if(!__g)return;"
                + "(function(GM,GM_getValue,GM_setValue,GM_deleteValue,GM_listValues,GM_notification,"
                + "GM_openInTab,GM_registerMenuCommand,GM_addStyle,GM_info,unsafeWindow){\n"
                + code
                + "\n}).call(window,__g,__g.getValue,__g.setValue,__g.deleteValue,__g.listValues,"
                + "__g.notification,__g.openInTab,__g.registerMenuCommand,window.__npAddStyle,"
                + "{script:{name:" + quote(name) + ",version:" + quote(version)
                + ",description:''},scriptHandler:'NorthPlus',version:'1.0.0'},window);"
                + "}catch(e){try{NP_SCRIPT.error(" + quote(id)
                + ",String(e&&e.message||e));}catch(x){}}})();";
        try {
            web.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    /** 脚本注册了菜单命令。 */
    public void onScriptMenuCommand(String id, String commandId, String caption) {
        if (TextUtils.isEmpty(commandId) || TextUtils.isEmpty(caption)) return;
        scriptMenus.put(commandId, caption);
    }

    /** 网页里的 .user.js 链接：直接当作安装请求处理。 */
    protected static boolean isUserscriptUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String low = url.toLowerCase(Locale.ROOT);
        int q = low.indexOf('?');
        if (q >= 0) low = low.substring(0, q);
        int h = low.indexOf('#');
        if (h >= 0) low = low.substring(0, h);
        return low.endsWith(".user.js");
    }

    protected void installUserscriptFrom(String url) {
        toast("正在下载用户脚本…");
        UserscriptInstaller.installFromUrl(this, url, (ok, message) -> {
            if (ok && web != null) {
                // 重新加载当前页，让新脚本立即生效
                web.reload();
            }
        });
    }

    /** 供脚本 GM_openInTab 使用：站内开新层，站外按用户设置处理。 */
    public void openUrlInNewLayer(String url) {
        if (TextUtils.isEmpty(url)) return;
        if (UrlMapper.isSite(url)) {
            String t = UrlMapper.standardize(url);
            if (!desktopMode) t = UrlMapper.toMobile(t);
            openLayer(t);
        } else {
            openExternal(url);
        }
    }

    private static String quote(String s) {
        return JSONObject.quote(s == null ? "" : s);
    }

    private void setupSwipe() {
        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.np_accent));
        swipe.setOnRefreshListener(() -> {
            showContent();
            if (web != null) web.reload();
        });
    }

    private void setupToolbar() {
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnRefresh).setOnClickListener(v -> reload());
        findViewById(R.id.btnMore).setOnClickListener(this::showMainMenu);
        findViewById(R.id.btnRetry).setOnClickListener(v -> reload());
        findViewById(R.id.btnSwitchSite).setOnClickListener(v -> switchToReachableSite());
        // 长按标题复制当前网址
        if (pageTitle != null) {
            pageTitle.setOnLongClickListener(v -> {
                copyCurrentLink();
                return true;
            });
        }
        setupLongPress();
    }

    protected void reload() {
        showContent();
        if (web != null) web.reload();
    }

    protected void onPageCommitted(String url) {
    }

    protected final void selectBottomNav(int index) {
        if (bottomNav == null || bottomNav.getChildCount() == 0) return;
        for (int i = 0; i < bottomNav.getChildCount(); i++) {
            View item = bottomNav.getChildAt(i);
            android.widget.ImageView icon = item.findViewById(R.id.navIcon);
            TextView label = item.findViewById(R.id.navLabel);
            boolean on = i == index;
            int color = ContextCompat.getColor(this,
                    on ? R.color.np_nav_active : R.color.np_nav_idle);
            if (icon != null) icon.setColorFilter(color);
            if (label != null) {
                label.setTextColor(color);
                label.setTypeface(null, on ? android.graphics.Typeface.BOLD
                        : android.graphics.Typeface.NORMAL);
            }
        }
    }

    // ---------------------------------------------------------------- 导航分层

    /** 该目标地址是否应该另开一层（而不是在当前界面内加载）。 */
    protected boolean canStackAnotherLayer(String target) {
        if (!UrlMapper.isThread(target)) return false;
        if (rootScreen()) return true;                       // 主界面点帖子 → 独立层
        if (screenDepth() >= Const.MAX_DEPTH) return false;  // 硬上限
        if (!UrlMapper.isBoard(entryUrl)) return false;      // 本层入口不是列表 → 全部留在本层
        String cur = web == null ? lastContentUrl : web.getUrl();
        return UrlMapper.isBoard(cur) && UrlMapper.isThread(target);
    }

    protected void openLayer(String url) {
        Intent i = new Intent(this, ThreadActivity.class);
        i.putExtra(Const.EXTRA_URL, url);
        i.putExtra(Const.EXTRA_DEPTH, screenDepth() + 1);
        startActivity(i);
        overridePendingTransition(0, 0);
        if (!rootScreen()) returningFromChild = true;
    }

    /** 刚开过子界面，onResume 时避免做重载动作。 */
    protected boolean returningFromChild = false;

    @Override
    public void onBackPressed() {
        if (rootScreen()) {
            // 主界面：按浏览器语义沿 WebView 历史后退，到顶后双击退出
            if (web != null && web.canGoBack()) {
                web.goBack();
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastBackPress < 2000L) {
                super.onBackPressed();
            } else {
                lastBackPress = now;
                toast(getString(R.string.exit_hint));
            }
            return;
        }

        // 独立层：退到入口就关闭本界面，避免站点自身重定向造成的回跳死循环
        if (web == null) {
            finish();
            return;
        }
        if (isAtEntryScreen()) {
            finish();
            return;
        }
        if (web.canGoBack()) {
            web.goBack();
            return;
        }
        finish();
    }

    /**
     * 是否已经退到本层入口。
     *
     * <p>不能简单写 {@code currentIndex <= 1}：入口页被 302 时会多占一格，
     * 而用户点进去的帖子同样可能落在下标 1。要按页面类型判断。
     */
    protected boolean isAtEntryScreen() {
        if (rootScreen() || web == null) return false;
        int idx;
        String itemUrl = null;
        try {
            android.webkit.WebBackForwardList list = web.copyBackForwardList();
            idx = list.getCurrentIndex();
            android.webkit.WebHistoryItem it = list.getItemAtIndex(idx);
            if (it != null) itemUrl = it.getUrl();
        } catch (Exception e) {
            return false;
        }
        if (idx <= 0) return true;
        if (TextUtils.isEmpty(itemUrl)) return idx == 1;
        if (UrlMapper.isThread(itemUrl)) return UrlMapper.sameThreadPage(itemUrl, entryUrl);
        return idx == 1;
    }

    /** 统一处理一次导航请求，返回 true 表示已消费。 */
    protected boolean handleNavigation(String rawUrl, boolean mainFrame) {
        if (TextUtils.isEmpty(rawUrl)) return false;
        if (rawUrl.startsWith("javascript:") || rawUrl.startsWith("about:")
                || rawUrl.startsWith("blob:") || rawUrl.startsWith("data:")) {
            return false;
        }
        if (isUserscriptUrl(rawUrl)) {
            installUserscriptFrom(rawUrl);
            return true;
        }
        if (isExternalScheme(rawUrl)) {
            openExternal(rawUrl);
            return true;
        }
        if (!UrlMapper.isSite(rawUrl)) {
            // 站外链接：默认也在应用内打开；关掉开关才交给系统浏览器
            if (inAppBrowsing() || prefs.inAppExternal()) {
                return false;
            }
            openExternal(rawUrl);
            return true;
        }
        // 站内链接：关掉「在应用内打开」就直接用系统浏览器
        if (!inAppBrowsing() && !prefs.inAppSite()) {
            openExternal(rawUrl);
            return true;
        }
        // 规范化：镜像域统一改写到主站域（否则会丢当前域登录态、并被当成站外链接甩出去）
        String target = UrlMapper.standardize(rawUrl);
        if (!desktopMode) target = UrlMapper.toMobile(target);

        if (target != null && !target.equals(rawUrl)) {
            if (canStackAnotherLayer(target)) {
                openLayer(target);
            } else if (web != null) {
                web.loadUrl(target);
            }
            return true;
        }
        if (canStackAnotherLayer(target)) {
            openLayer(target);
            return true;
        }
        return false;
    }

    private static boolean isExternalScheme(String url) {
        String low = url.toLowerCase();
        for (String s : EXTERNAL_SCHEMES) {
            if (low.startsWith(s)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 注入

    protected void inject() {
        if (web == null) return;
        String js = Js.build(prefs.night(), prefs.noImage(), prefs.adBlock(), true);
        try {
            web.evaluateJavascript(js, null);
        } catch (Exception ignored) {
        }
    }

    protected void showContent() {
        if (errorView != null) errorView.setVisibility(View.GONE);
        if (swipe != null) swipe.setVisibility(View.VISIBLE);
    }

    protected void showError(String detail) {
        if (errorDetail != null) {
            errorDetail.setText(TextUtils.isEmpty(detail)
                    ? getString(R.string.net_error_desc) : detail);
        }
        if (errorView != null) errorView.setVisibility(View.VISIBLE);
    }

    protected void toast(String msg) {
        if (!TextUtils.isEmpty(msg)) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- 菜单

    /** 右上角菜单：只保留「当前地址 / 复制链接 / 设置 / 脚本设置」+ 脚本注册的命令。 */
    protected void showMainMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);

        // 当前地址：点进去就是「访问站点」选择页
        final String host = Site.host();
        pm.getMenu().add(0, MENU_SITE, 0,
                getString(R.string.menu_current_site, Site.displayName(host), host));
        pm.getMenu().add(0, MENU_COPY, 1, getString(R.string.menu_share));
        pm.getMenu().add(0, MENU_SETTINGS, 2, getString(R.string.menu_settings));
        pm.getMenu().add(0, MENU_SCRIPTS, 3, getString(R.string.menu_userscripts));

        // 脚本通过 GM_registerMenuCommand 注册的命令（没有就不占位）
        final Map<Integer, String> scriptOrder = new LinkedHashMap<>();
        int n = 0;
        for (Map.Entry<String, String> e : scriptMenus.entrySet()) {
            int itemId = 1000 + n;
            pm.getMenu().add(0, itemId, 10 + n, "脚本 · " + e.getValue());
            scriptOrder.put(itemId, e.getKey());
            n++;
        }

        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id >= 1000) {
                runScriptCommand(scriptOrder.get(id));
                return true;
            }
            switch (id) {
                case MENU_SITE:
                    startActivity(new Intent(this, SitesActivity.class));
                    return true;
                case MENU_COPY:
                    copyCurrentLink();
                    return true;
                case MENU_SETTINGS:
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                case MENU_SCRIPTS:
                    startActivity(new Intent(this, UserscriptsActivity.class));
                    return true;
                default:
                    return false;
            }
        });
        pm.show();
    }

    // ---------------------------------------------------------------- 长按复制

    /** 长按网页：图片 / 链接弹复制菜单；纯文本交给 WebView 原生的选择与复制。 */
    private void setupLongPress() {
        if (web == null) return;
        web.setLongClickable(true);
        web.setOnLongClickListener(v -> {
            WebView.HitTestResult r = web.getHitTestResult();
            if (r == null) return false;
            String extra = r.getExtra();
            if (TextUtils.isEmpty(extra)) return false;
            switch (r.getType()) {
                case WebView.HitTestResult.IMAGE_TYPE:
                case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE:
                    showImageMenu(UrlMapper.absolutize(extra));
                    return true;
                case WebView.HitTestResult.SRC_ANCHOR_TYPE:
                    showLinkMenu(UrlMapper.absolutize(extra));
                    return true;
                default:
                    return false;
            }
        });
    }

    private void showImageMenu(final String url) {
        String[] items = {"全屏查看", "复制图片地址", "保存图片", "用浏览器打开"};
        new AlertDialog.Builder(this)
                .setTitle(shorten(url))
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            openImageViewer(url);
                            break;
                        case 1:
                            copyText(url, getString(R.string.link_copied));
                            break;
                        case 2:
                            saveImage(url);
                            break;
                        default:
                            openExternal(url);
                            break;
                    }
                })
                .show();
    }

    private void showLinkMenu(final String url) {
        String[] items = {"复制链接地址", "用浏览器打开"};
        new AlertDialog.Builder(this)
                .setTitle(shorten(url))
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        copyText(url, getString(R.string.link_copied));
                    } else {
                        openExternal(url);
                    }
                })
                .show();
    }

    private static String shorten(String s) {
        return s != null && s.length() > 64 ? s.substring(0, 64) + "…" : s;
    }

    private void copyText(String text, String hint) {
        if (TextUtils.isEmpty(text)) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("northplus", text));
            toast(hint);
        }
    }

    private void openImageViewer(String url) {
        ArrayList<String> list = new ArrayList<>();
        list.add(url);
        Intent i = new Intent(this, ImageViewerActivity.class);
        i.putStringArrayListExtra(ImageViewerActivity.EXTRA_LIST, list);
        i.putExtra(ImageViewerActivity.EXTRA_INDEX, 0);
        startActivity(i);
    }

    private void saveImage(final String url) {
        toast("正在保存图片…");
        final Context ctx = getApplicationContext();
        App.EXEC.execute(() -> {
            byte[] raw = ImgLoader.fetch(url);
            String msg;
            if (raw == null) {
                msg = getString(R.string.save_failed);
            } else {
                try {
                    msg = getString(R.string.saved) + "：" + Gallery.save(ctx, url, raw);
                } catch (Exception e) {
                    msg = getString(R.string.save_failed) + "：" + e.getMessage();
                }
            }
            final String m = msg;
            App.runOnUi(() -> {
                if (!isFinishing()) toast(m);
            });
        });
    }

    private void runScriptCommand(String commandId) {
        if (web == null || TextUtils.isEmpty(commandId)) return;
        try {
            web.evaluateJavascript(
                    "window.__npRunMenu&&window.__npRunMenu(" + quote(commandId) + ")", null);
        } catch (Exception ignored) {
        }
    }

    protected void copyCurrentLink() {
        String url = web == null ? null : web.getUrl();
        copyText(url, getString(R.string.link_copied));
    }

    /** 电脑版 ⇄ 移动版切换，结果写回设置（下次启动沿用）。 */
    protected void togglePageMode() {
        final boolean toMobile = desktopMode;
        prefs.setMobileMode(toMobile);
        desktopMode = !toMobile;
        if (web == null) return;
        WebSettings s = web.getSettings();
        s.setUserAgentString(desktopMode ? Const.UA_DESKTOP : Const.UA_MOBILE);
        s.setBuiltInZoomControls(prefs.pinchZoom());
        s.setSupportZoom(prefs.pinchZoom());
        String cur = web.getUrl();
        if (TextUtils.isEmpty(cur)) cur = entryUrl;
        String target = desktopMode ? UrlMapper.toDesktop(cur) : UrlMapper.toMobile(cur);
        web.loadUrl(target);
        toast(desktopMode ? "已切换到电脑版" : "已切换到移动版");
    }

    protected void openExternal(String url) {
        if (TextUtils.isEmpty(url)) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("没有可打开该链接的应用");
        } catch (Exception e) {
            toast("打开失败");
        }
    }

    // ---------------------------------------------------------------- 下载

    private final DownloadListener downloadListener =
            (url, userAgent, contentDisposition, mimetype, contentLength) -> {
                if (isUserscriptUrl(url)) {
                    installUserscriptFrom(url);
                    return;
                }
                if (TextUtils.isEmpty(url) || url.startsWith("blob:") || url.startsWith("data:")) {
                    toast("该链接无法直接下载，可用浏览器打开");
                    return;
                }
                try {
                    String name = URLUtil.guessFileName(url, contentDisposition, mimetype);
                    DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                    r.setMimeType(mimetype);
                    r.addRequestHeader("User-Agent", Const.UA);
                    r.addRequestHeader("Referer", Site.base() + "/");
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (!TextUtils.isEmpty(cookie)) r.addRequestHeader("Cookie", cookie);
                    r.setTitle(name);
                    r.setDescription(getString(R.string.app_name));
                    r.setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS, name);
                    DownloadManager dm =
                            (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(r);
                        toast("已加入下载：" + name);
                        return;
                    }
                } catch (Exception ignored) {
                    // 落到浏览器处理
                }
                openExternal(url);
            };

    // ---------------------------------------------------------------- WebBridge.Host

    @Override
    public void onOpenImages(String imagesJson, int index) {
        ArrayList<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(imagesJson);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, null);
                if (!TextUtils.isEmpty(s)) list.add(UrlMapper.absolutize(s));
            }
        } catch (Exception ignored) {
        }
        if (list.isEmpty()) return;
        Intent i = new Intent(this, ImageViewerActivity.class);
        i.putStringArrayListExtra(ImageViewerActivity.EXTRA_LIST, list);
        i.putExtra(ImageViewerActivity.EXTRA_INDEX,
                Math.max(0, Math.min(index, list.size() - 1)));
        startActivity(i);
        overridePendingTransition(0, 0);
    }

    @Override
    public void onOpenUrl(String url) {
        handleNavigation(url, true);
    }

    @Override
    public void onToast(String msg) {
        toast(msg);
    }

    // ---------------------------------------------------------------- WebViewClient

    private class Client extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleNavigation(request.getUrl().toString(), request.isForMainFrame());
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleNavigation(url, true);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (!TextUtils.isEmpty(url)) lastContentUrl = url;
            pageLoadError = false;
            if (progress != null) progress.setVisibility(View.VISIBLE);
            if (hostInjectOnPageStarted && prefs.scriptsEnabled()) {
                String js = hostJs();
                if (!js.isEmpty()) {
                    try {
                        view.evaluateJavascript(js, null);
                    } catch (Exception ignored) {
                    }
                }
            }
            onPageCommitted(url);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            super.onPageCommitVisible(view, url);
            inject();   // 尽早套上夜间配色，减少白底闪烁
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            inject();
            if (swipe != null) swipe.setRefreshing(false);
            if (progress != null) progress.setVisibility(View.GONE);
            if (!pageLoadError) showContent();
            String title = view.getTitle();
            if (!TextUtils.isEmpty(title) && pageTitle != null) {
                pageTitle.setText(shortTitle(title));
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                                    WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                pageLoadError = true;
                if (swipe != null) swipe.setRefreshing(false);
                if (progress != null) progress.setVisibility(View.GONE);
                showError(error == null ? null : String.valueOf(error.getDescription()));
            }
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view,
                                                          WebResourceRequest request) {
            if (!prefs.adBlock()) return null;
            String u = request.getUrl().toString().toLowerCase();
            if (request.isForMainFrame()) return null;
            if (u.contains("mobileads")) {
                return new WebResourceResponse("image/gif", "utf-8",
                        new ByteArrayInputStream(TRANSPARENT_GIF));
            }
            if (u.contains("googletagmanager.com/gtag/js")) {
                return new WebResourceResponse("application/javascript", "utf-8",
                        new ByteArrayInputStream(new byte[0]));
            }
            return null;
        }
    }

    /** 站点标题形如「帖子标题 版块名 - 南+ South Plus」，压成短标题。 */
    private static String shortTitle(String t) {
        String s = t.replace("- powered by Pu!mdHd", "").replace("powered by Pu!mdHd", "");
        int nl = s.indexOf('\n');
        if (nl > 0) s = s.substring(0, nl);
        s = s.replace(" - 南+ South Plus", "").replace(" 南+ South Plus", "").trim();
        if (s.length() > 30) s = s.substring(0, 29) + "…";
        return s;
    }

    // ---------------------------------------------------------------- WebChromeClient

    private class Chrome extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (progress != null) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                      Message resultMsg) {
            if (!isUserGesture || resultMsg == null) {
                return true;   // 吞掉非用户触发的自动弹窗
            }
            final WebView tmp = new WebView(BaseWebActivity.this);
            WebSettings ts = tmp.getSettings();
            ts.setJavaScriptEnabled(true);
            ts.setUserAgentString(Const.UA);
            tmp.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    return handleNavigation(r.getUrl().toString(), true);
                }

                @SuppressWarnings("deprecation")
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, String url) {
                    return handleNavigation(url, true);
                }
            });
            try {
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(tmp);
                resultMsg.sendToTarget();
            } catch (Exception ignored) {
            }
            return true;
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> cb,
                                         FileChooserParams params) {
            if (pendingFileCallback != null) {
                pendingFileCallback.onReceiveValue(null);
                pendingFileCallback = null;
            }
            pendingFileCallback = cb;
            try {
                Intent intent = params.createIntent();
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, "选择文件"), REQ_FILE);
                return true;
            } catch (Exception e) {
                pendingFileCallback = null;
                cb.onReceiveValue(null);
                return false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQ_FILE) {
            if (pendingFileCallback != null) {
                Uri[] result = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getClipData() != null) {
                        int n = data.getClipData().getItemCount();
                        result = new Uri[n];
                        for (int i = 0; i < n; i++) {
                            result[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else if (data.getData() != null) {
                        result = new Uri[]{data.getData()};
                    }
                }
                pendingFileCallback.onReceiveValue(result);
                pendingFileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
